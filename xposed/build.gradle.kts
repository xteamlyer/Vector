@Suppress("UNCHECKED_CAST")
val versionCodeProvider = rootProject.extra["versionCodeProvider"] as Provider<String>
@Suppress("UNCHECKED_CAST")
val versionNameProvider = rootProject.extra["versionNameProvider"] as Provider<String>

/**
 * Whoever is building this framework, which is who a module is loaded by.
 *
 * Included in another build -- a patcher that ships this code inside an app rather than a daemon --
 * the framework a module answers to is that build's, so its name is the one reported. The version
 * already resolves that way, being read from the repository the build was invoked in.
 */
val frameworkName: String =
    runCatching { gradle.parent?.rootProject?.name }.getOrNull() ?: rootProject.name

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.ktfmt)
}

ktfmt { kotlinLangStyle() }

android {
    namespace = "org.matrix.vector.impl"

    androidResources { enable = false }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "FRAMEWORK_NAME", """"${frameworkName}"""")
        buildConfigField("String", "VERSION_NAME", """"${versionNameProvider.get()}"""")
        buildConfigField("long", "VERSION_CODE", versionCodeProvider.get())
    }

    sourceSets {
        named("main") {
            java.directories.addAll(listOf("src/main/kotlin", "libxposed/api/src/main/java"))
        }
    }
}

dependencies {
    implementation(projects.external.axml)
    implementation(projects.hiddenapi.bridge)
    implementation(projects.services.daemonService)
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.libxposed.annotation)
    compileOnly(projects.hiddenapi.stubs)
}
