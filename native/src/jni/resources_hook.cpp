#include <dex_builder.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <string>

#include "common/config.h"
#include "elf/elf_image.h"
#include "elf/symbol_cache.h"
#include "framework/android_types.h"
#include "jni/jni_bridge.h"
#include "jni/jni_hooks.h"
#include "jni/resources_hook.h"

namespace vector::native::jni {

// --- Type Aliases for Native Android Framework Functions ---

// Signature for android::ResXMLParser::getAttributeNameID(size_t)
using TYPE_GET_ATTR_NAME_ID = int32_t (*)(void *, size_t);
// Signature for android::ResXMLParser::getAttributeNameResID(size_t)
using TYPE_GET_ATTR_NAME_RES_ID = uint32_t (*)(void *, size_t);
// Signature for android::ResXMLParser::getStrings()
using TYPE_GET_STRINGS = const android::ResStringPool *(*)(void *);
// Signature for android::ResXMLParser::restart()
using TYPE_RESTART = void (*)(void *);
// Signature for android::ResXMLParser::next()
using TYPE_NEXT = int32_t (*)(void *);

// --- JNI Globals & Cached IDs ---
static jclass classXResources;
static jmethodID methodXResourcesTranslateAttrId;
static jmethodID methodXResourcesTranslateResId;

// --- Native Function Pointers ---
// To store the memory addresses of the private Android framework functions.
static TYPE_NEXT ResXMLParser_next = nullptr;
static TYPE_RESTART ResXMLParser_restart = nullptr;
static TYPE_GET_ATTR_NAME_ID ResXMLParser_getAttributeNameID = nullptr;
static TYPE_GET_ATTR_NAME_RES_ID ResXMLParser_getAttributeNameResID = nullptr;
static TYPE_GET_STRINGS ResXMLParser_getStrings = nullptr;

/**
 * @brief Constructs the class name for the XResources class at runtime.
 */
static std::string GetXResourcesClassName() {
    // Use a static local variable to ensure this lookup and string manipulation
    // only happens once.
    static std::string name = []() {
        auto &obfs_map = ConfigBridge::GetInstance()->obfuscation_map();
        if (obfs_map.empty()) {
            LOGW("GetXResourcesClassName: obfuscation_map is empty.");
        }
        // The key is the original, unobfuscated class name prefix.
        // The value is the new, obfuscated prefix.
        auto it = obfs_map.find("android.content.res.XRes");
        if (it == obfs_map.end()) {
            LOGE("Could not find obfuscated name for XResources.");
            return std::string();
        }
        std::string jni_name = it->second + "ources";
        LOGD("Resolved XResources class name to: {}", jni_name.c_str());
        return jni_name;
    }();
    return name;
}

/**
 * @brief Finds and caches the addresses of private functions in libandroidfw.so.
 *
 * It uses the ElfImage utility to parse the Android framework's shared library in memory,
 * find functions by their C++ mangled names, and
 * store their addresses in our global function pointers.
 *
 * @return True if all required symbols were found, false otherwise.
 */
static bool PrepareSymbols() {
    ElfImage fw(kFrameworkLibraryName);
    if (!fw.IsValid()) {
        LOGE("Failed to open Android framework library.");
        return false;
    };

    // The mangled names are specific to the compiler and architecture.
    // This is a very fragile part of the hook.

    // Find android::ResXMLParser::next()
    if (!(ResXMLParser_next = fw.getSymbAddress<TYPE_NEXT>("_ZN7android12ResXMLParser4nextEv"))) {
        LOGE("Failed to find symbol: ResXMLParser::next");
        return false;
    }
    // Find android::ResXMLParser::restart()
    if (!(ResXMLParser_restart =
              fw.getSymbAddress<TYPE_RESTART>("_ZN7android12ResXMLParser7restartEv"))) {
        LOGE("Failed to find symbol: ResXMLParser::restart");
        return false;
    };
    // Find android::ResXMLParser::getAttributeNameID(unsigned int/long)
    if (!(ResXMLParser_getAttributeNameID = fw.getSymbAddress<TYPE_GET_ATTR_NAME_ID>(
              LP_SELECT("_ZNK7android12ResXMLParser18getAttributeNameIDEj",
                        "_ZNK7android12ResXMLParser18getAttributeNameIDEm")))) {
        LOGE("Failed to find symbol: ResXMLParser::getAttributeNameID");
        return false;
    }
    // The next two are only needed for the attribute name half of the rewrite, so a library that
    // does not export them costs that half rather than the whole resource hook.
    // Find android::ResXMLParser::getAttributeNameResID(unsigned int/long)
    if (!(ResXMLParser_getAttributeNameResID = fw.getSymbAddress<TYPE_GET_ATTR_NAME_RES_ID>(
              LP_SELECT("_ZNK7android12ResXMLParser21getAttributeNameResIDEj",
                        "_ZNK7android12ResXMLParser21getAttributeNameResIDEm")))) {
        LOGW("Failed to find symbol: ResXMLParser::getAttributeNameResID");
    }
    // Find android::ResXMLParser::getStrings()
    if (!(ResXMLParser_getStrings =
              fw.getSymbAddress<TYPE_GET_STRINGS>("_ZNK7android12ResXMLParser10getStringsEv"))) {
        LOGW("Failed to find symbol: ResXMLParser::getStrings");
    }
    // Initialize another part of the resource framework that we depend on.
    return android::ResStringPool::setup(lsplant::InitInfo{
        .art_symbol_resolver = [&](auto s) { return fw.template getSymbAddress<>(s); }});
}

/**
 * @brief JNI entry point to initialize the entire native resources hook.
 */
VECTOR_DEF_NATIVE_METHOD(jboolean, ResourcesHook, initXResourcesNative) {
    const auto x_resources_class_name = GetXResourcesClassName();
    if (x_resources_class_name.empty()) {
        return JNI_FALSE;
    }

    if (auto classXResources_ =
            Context::GetInstance()->FindClassFromCurrentLoader(env, x_resources_class_name)) {
        classXResources = JNI_NewGlobalRef(env, classXResources_);
    } else {
        LOGE("Error while loading XResources class '{}'", x_resources_class_name.c_str());
        return JNI_FALSE;
    }

    // Dynamically build the method signature using the (possibly obfuscated) class name.
    std::string x_resources_jni_name = "L" + x_resources_class_name + ";";
    std::replace(x_resources_jni_name.begin(), x_resources_jni_name.end(), '.', '/');

    // Wrapped, like the lookup below: a missing method throws NoSuchMethodError, and the raw form
    // returned JNI_FALSE to Java with that exception still pending, so the caller saw a throw where
    // it had asked for a boolean.
    methodXResourcesTranslateResId = lsplant::JNI_GetStaticMethodID(
        env, classXResources, "translateResId",
        fmt::format("(I{}Landroid/content/res/Resources;)I", x_resources_jni_name));
    if (!methodXResourcesTranslateResId) {
        LOGE("Failed to find method: XResources.translateResId");
        return JNI_FALSE;
    }

    methodXResourcesTranslateAttrId = lsplant::JNI_GetStaticMethodID(
        env, classXResources, "translateAttrId",
        fmt::format("(Ljava/lang/String;{})I", x_resources_jni_name));
    if (!methodXResourcesTranslateAttrId) {
        LOGE("Failed to find method: XResources.translateAttrId");
        return JNI_FALSE;
    }

    if (!PrepareSymbols()) {
        LOGE("Failed to prepare native symbols for resource hooking.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * @brief Removes the 'final' modifier from a Java class at runtime.
 * This allows the framework to create subclasses of what are normally final classes.
 */
VECTOR_DEF_NATIVE_METHOD(jboolean, ResourcesHook, makeInheritable, jclass target_class) {
    if (lsplant::MakeClassInheritable(env, target_class)) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * @brief Builds a new ClassLoader in memory containing dynamically generated classes.
 *
 * This function creates a DEX file on-the-fly.
 * The DEX file contains dummy classes that inherit from key Android resource classes.
 * This allows the framework to inject its own logic by later creating classes that
 * inherit from these dummies.
 *
 * @return A new dalvik.system.InMemoryDexClassLoader instance.
 */
jobject BuildDummySuperClassLoader(JNIEnv *env, jobject parent, const char *res_super,
                                   const char *ta_super) {
    using namespace startop::dex;

    // Cache the class and constructor for InMemoryDexClassLoader.
    static auto in_memory_classloader =
        lsplant::JNI_NewGlobalRef(env, lsplant::JNI_FindClass(env, "dalvik/system/InMemoryDexClassLoader"));
    static jmethodID initMid = lsplant::JNI_GetMethodID(
        env, in_memory_classloader, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    if (!in_memory_classloader || !initMid) return nullptr;

    DexBuilder dex_file;

    // Create a class named "xposed.dummy.XResourcesSuperClass".
    ClassBuilder xresource_builder{dex_file.MakeClass("xposed/dummy/XResourcesSuperClass")};
    // Set its superclass to the one specified by the caller.
    xresource_builder.setSuperClass(TypeDescriptor::FromClassname(res_super));

    // Create a class named "xposed.dummy.XTypedArraySuperClass".
    ClassBuilder xtypearray_builder{dex_file.MakeClass("xposed/dummy/XTypedArraySuperClass")};
    // Set its superclass.
    xtypearray_builder.setSuperClass(TypeDescriptor::FromClassname(ta_super));

    // Finalize the DEX file into a memory buffer.
    slicer::MemView image{dex_file.CreateImage()};

    // Wrap the memory buffer in a Java ByteBuffer.
    auto dex_buffer = lsplant::JNI_NewDirectByteBuffer(env, const_cast<void *>(image.ptr()),
                                                       image.size());
    if (!dex_buffer) return nullptr;

    // Create and return a new InMemoryDexClassLoader instance. Released from its scope because it
    // is handed straight back to the caller.
    return lsplant::JNI_NewObject(env, in_memory_classloader, initMid, dex_buffer, parent)
        .release();
}

VECTOR_DEF_NATIVE_METHOD(jobject, ResourcesHook, buildDummyClassLoader, jobject parent,
                         jstring resource_super_class, jstring typed_array_super_class) {
    return BuildDummySuperClassLoader(env, parent,
                                      lsplant::JUTFString(env, resource_super_class).get(),
                                      lsplant::JUTFString(env, typed_array_super_class).get());
}

/**
 * @brief Reports whether the pages spanning [addr, addr + len) are mapped.
 *
 * msync on an unmapped range fails with ENOMEM, which turns a read that would raise SIGSEGV into an
 * answer. The search below walks off the end of a struct whose size it does not know, so it needs
 * one.
 *
 * hook_bridge.cpp has the same helper, also with internal linkage. Folding the two into a shared
 * header is a follow-up; both are being edited in this round.
 */
static bool IsMapped(uintptr_t addr, size_t len) {
    static const size_t page = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    if (page == 0) return false;
    const uintptr_t start = addr & ~(page - 1);
    const size_t span = ((addr + len) - start + page - 1) & ~(page - 1);
    return msync(reinterpret_cast<void *>(start), span, MS_ASYNC) == 0;
}

// The attribute name map is the member behind the string pool, so the distance to it is
// sizeof(ResStringPool), and that grows with almost every release: 0x80 on Android 10 and 0x130 on
// Android 17 for LP64. The bracket has to be per ABI, because every member of that class is a word
// and its lock is a single int on ILP32, which puts the same layout at 0x30 there - below the LP64
// floor, so a shared floor would start the search past what it is looking for. The ceilings are set
// so that the last slot the scan reads is still inside the ResXMLTree allocation on the newest
// release measured: msync answers for a page, not for a malloc chunk, and a process with heap
// tagging on faults on the word after the object rather than returning garbage.
static constexpr size_t kMinMapOffset = LP_SELECT(0x20, 0x40);
static constexpr size_t kMaxMapOffset = LP_SELECT(0xa0, 0x140);
// The map holds one id per string in the document's own pool, so a count this large is not a
// candidate but a coincidence.
static constexpr size_t kMaxMapEntries = 0x4000;
// Where the search landed: zero until it has run, kMapUnusable once it has given up. Inflation is
// not single threaded - RemoteViews and AsyncLayoutInflater both do it off the main thread - so the
// one variable that gates a raw write into framework memory is not left to luck.
static constexpr size_t kMapUnusable = ~static_cast<size_t>(0);
static std::atomic<size_t> attr_map_offset{0};
// getAttributeNameResID() runs the raw id through the dynamic reference table, so a module built as
// a shared library answers differently from the map it is reading and fails the match below. One
// such document is not proof that the offset cannot be found, so give up only after a few.
static constexpr int kMaxMapSearches = 3;
static std::atomic<int> attr_map_searches{0};

/**
 * @brief Reports whether a candidate array is the map the parser is reading its attributes from.
 *
 * Every attribute the parser answers with a non-zero id has to come back out of the array at the
 * index the parser names for it, which for a tag with several attributes leaves no room for a
 * coincidence.
 */
static bool MapsCurrentAttributes(void *parser, const uint32_t *map, size_t count,
                                  size_t attrCount) {
    bool matched = false;
    for (size_t idx = 0; idx < attrCount; idx++) {
        auto resID = ResXMLParser_getAttributeNameResID(parser, idx);
        if (resID == 0) continue;
        auto nameID = ResXMLParser_getAttributeNameID(parser, idx);
        if (nameID < 0 || static_cast<size_t>(nameID) >= count) return false;
        if (map[nameID] != resID) return false;
        matched = true;
    }
    return matched;
}

/**
 * @brief Returns the distance from the string pool to the attribute name map, or zero.
 */
static size_t FindAttributeNameMap(void *parser, uintptr_t pool, size_t attrCount) {
    for (size_t off = kMinMapOffset; off <= kMaxMapOffset; off += sizeof(void *)) {
        if (!IsMapped(pool + off, sizeof(void *) + sizeof(size_t))) break;
        auto candidate = *reinterpret_cast<uint32_t *const *>(pool + off);
        auto count = *reinterpret_cast<const size_t *>(pool + off + sizeof(void *));
        if (candidate == nullptr || count == 0 || count > kMaxMapEntries) continue;
        if (reinterpret_cast<uintptr_t>(candidate) % alignof(uint32_t) != 0) continue;
        if (!IsMapped(reinterpret_cast<uintptr_t>(candidate), count * sizeof(uint32_t))) continue;
        if (!MapsCurrentAttributes(parser, candidate, count, attrCount)) continue;
        return off;
    }
    return 0;
}

/**
 * @brief Returns the writable slot holding an attribute's mapped resource id, or nullptr.
 *
 * That map is what turns an attribute's string index into the resource id the inflater looks it up
 * by, so rewriting it is the only way a replacement layout can carry attributes of its own.
 * getAttributeNameResID() reads it but nothing exported writes it, so its address has to be found.
 * Hard-coding the offset is what killed this rewrite in Android 10: the map is the member behind
 * ResStringPool, and that class has since gained a vtable, a decode lock and a lookup cache. So
 * look for the slot instead - once per process, the layout being the same for every document - and
 * leave the attribute names alone if nothing matches, which is what happened on every release after
 * Pie anyway.
 *
 * @param searchedHere Whether this document has already paid for a search, so a failing one costs
 *                     the bracket once rather than once per attribute.
 */
static uint32_t *AttributeNameSlot(void *parser, const android::ResStringPool *strings,
                                   size_t attrCount, int32_t nameID, uint32_t resID,
                                   bool &searchedHere) {
    const auto pool = reinterpret_cast<uintptr_t>(strings);
    auto offset = attr_map_offset.load(std::memory_order_relaxed);
    if (offset == kMapUnusable) return nullptr;
    if (offset == 0) {
        if (searchedHere) return nullptr;
        searchedHere = true;
        offset = FindAttributeNameMap(parser, pool, attrCount);
        if (offset == 0) {
            if (attr_map_searches.fetch_add(1, std::memory_order_relaxed) + 1 < kMaxMapSearches)
                return nullptr;
            // And give up only if nothing has found it meanwhile: a document that another thread
            // matched is the answer, whatever this one failed to match.
            size_t unset = 0;
            if (!attr_map_offset.compare_exchange_strong(unset, kMapUnusable,
                                                         std::memory_order_relaxed))
                return nullptr;
            LOGW("Could not locate the attribute name map, leaving attribute names untranslated.");
            return nullptr;
        }
        attr_map_offset.store(offset, std::memory_order_relaxed);
    }

    // The offset was matched against one document; every other one gets the cheap half of the same
    // check, so a slot that has stopped being the map is skipped instead of written through. The
    // count sits in the word behind the pointer, which answers exactly what a page probe could only
    // approximate, and without a syscall per attribute.
    auto map = *reinterpret_cast<uint32_t *const *>(pool + offset);
    auto count = *reinterpret_cast<const size_t *>(pool + offset + sizeof(void *));
    if (map == nullptr || count > kMaxMapEntries) return nullptr;
    if (static_cast<size_t>(nameID) >= count) return nullptr;
    auto slot = map + static_cast<size_t>(nameID);
    return *slot == resID ? slot : nullptr;
}

/**
 * @brief The core resource rewriting function.
 *
 * This method iterates through a binary XML file as it's being parsed by the Android framework.
 * For each attribute and value, it calls back to Java to see
 * if the resource ID should be replaced with a different one.
 *
 * @param parserPtr A raw pointer to the native android::ResXMLParser object.
 * @param origRes The original XResources object.
 * @param repRes The replacement Resources object.
 */
VECTOR_DEF_NATIVE_METHOD(void, ResourcesHook, rewriteXmlReferencesNative, jlong parserPtr,
                         jobject origRes, jobject repRes) {
    // Cast the long from Java back to a native C++ pointer.
    // This is dangerous and assumes the Java code provides a valid pointer.
    auto parser = (android::ResXMLParser *)parserPtr;

    if (parser == nullptr) return;

    // Everything behind the parser is reached through the framework's own accessors: the tree used
    // to be read at fixed offsets, which stopped describing it in Android 10 and silently skipped
    // the whole attribute name half of this rewrite from then on. Without those accessors only the
    // values below are translated, which is all that happened on any release after Pie anyway.
    auto strings =
        ResXMLParser_getStrings != nullptr && ResXMLParser_getAttributeNameResID != nullptr
            ? ResXMLParser_getStrings(parser)
            : nullptr;
    android::ResXMLTree_attrExt *tag;
    size_t attrCount;
    bool searchedHere = false;

    // This loop iterates through all tokens in the binary XML file.
    do {
        // Call the native android::ResXMLParser::next() function via our pointer.
        switch (ResXMLParser_next(parser)) {
        case android::ResXMLParser::START_TAG:
            tag = (android::ResXMLTree_attrExt *)parser->mCurExt;
            attrCount = tag->attributeCount;
            // Loop through all attributes of the current XML tag.
            for (size_t idx = 0; idx < attrCount; idx++) {
                auto attr =
                    (android::ResXMLTree_attribute *)(((const uint8_t *)tag) + tag->attributeStart +
                                                      tag->attributeSize * idx);

                // Translate the attribute name's resource ID ---
                // e.g., for 'android:textColor', translate the ID for 'textColor'.
                int32_t attrNameID = ResXMLParser_getAttributeNameID(parser, idx);
                uint32_t oldAttrResID =
                    strings != nullptr ? ResXMLParser_getAttributeNameResID(parser, idx) : 0;

                // Only replace IDs that belong to the app's package (0x7f...), and only where the
                // map can be written back: a slot that was found by searching is trusted for
                // exactly as long as it keeps answering the same as the parser does.
                uint32_t *nameSlot = attrNameID >= 0 && oldAttrResID >= 0x7f000000
                                         ? AttributeNameSlot(parser, strings, attrCount, attrNameID,
                                                             oldAttrResID, searchedHere)
                                         : nullptr;
                if (nameSlot != nullptr) {
                    auto attrName = strings->stringAt(attrNameID);
                    // An index the pool cannot decode comes back empty, and NewString(nullptr, 0)
                    // is a JNI misuse that aborts the process under CheckJNI.
                    if (attrName.data_ != nullptr) {
                        jstring attrNameStr =
                            env->NewString((const jchar *)attrName.data_, attrName.length_);
                        if (env->ExceptionCheck()) goto leave;  // Critical check

                        // Call back to Java: XResources.translateAttrId(String name, ...)
                        jint attrResID = env->CallStaticIntMethod(
                            classXResources, methodXResourcesTranslateAttrId, attrNameStr, origRes);
                        env->DeleteLocalRef(attrNameStr);
                        if (env->ExceptionCheck()) goto leave;

                        // Directly modify the resource ID table in the parser's memory.
                        *nameSlot = attrResID;
                    }
                }

                // Translate the attribute's value if it's a reference ---
                // e.g., for 'android:textColor="@color/my_text"', translate the ID for
                // '@color/my_text'.
                if (attr->typedValue.dataType != android::Res_value::TYPE_REFERENCE) continue;

                jint oldValue = attr->typedValue.data;
                if (oldValue < 0x7f000000) continue;

                // Call back to Java: XResources.translateResId(int id, ...)
                jint newValue = env->CallStaticIntMethod(
                    classXResources, methodXResourcesTranslateResId, oldValue, origRes, repRes);
                if (env->ExceptionCheck()) goto leave;

                // If the ID was changed, update the value directly in the parser's
                // memory.
                if (newValue != oldValue) attr->typedValue.data = newValue;
            }
            continue;
        case android::ResXMLParser::END_DOCUMENT:
        case android::ResXMLParser::BAD_DOCUMENT:
            goto leave;  // Exit the loop.
        default:
            continue;  // Process next XML token.
        }
    } while (true);

// A single exit point for the function.
leave:
    // Reset the parser to its initial state so it can be read again.
    ResXMLParser_restart(parser);
}

// JNI method registration table.
static JNINativeMethod gMethods[] = {
    VECTOR_NATIVE_METHOD(ResourcesHook, initXResourcesNative, "()Z"),
    VECTOR_NATIVE_METHOD(ResourcesHook, makeInheritable, "(Ljava/lang/Class;)Z"),
    VECTOR_NATIVE_METHOD(ResourcesHook, buildDummyClassLoader,
                         "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/"
                         "String;)Ljava/lang/ClassLoader;"),
    VECTOR_NATIVE_METHOD(ResourcesHook, rewriteXmlReferencesNative,
                         "(JLjava/lang/Object;Landroid/content/res/Resources;)V")};

void RegisterResourcesHook(JNIEnv *env) { REGISTER_VECTOR_NATIVE_METHODS(ResourcesHook); }
}  // namespace vector::native::jni
