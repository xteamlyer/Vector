#pragma once

#include <jni.h>

namespace vector::native::jni {

/**
 * Builds an InMemoryDexClassLoader that defines xposed.dummy.XResourcesSuperClass (extending
 * res_super) and xposed.dummy.XTypedArraySuperClass (extending ta_super), delegating to parent.
 * Returns a new local reference, or nullptr on failure.
 *
 * Exposed so an injector that constructs the framework class loader itself can install this loader
 * as the framework loader's parent at construction time. That is the only point early enough to
 * keep XResources' synthetic super resolvable when the framework is loaded into an already-running
 * app: there is no zygote window in which the super could be generated first, and once XResources
 * is resolved with its super unreachable ART records the class as erroneous for the whole process.
 */
jobject BuildDummySuperClassLoader(JNIEnv *env, jobject parent, const char *res_super,
                                   const char *ta_super);

}  // namespace vector::native::jni
