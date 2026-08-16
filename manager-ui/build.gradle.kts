// Shared manager UI, consumed by both Vector's manager app and LSPatch's manager (via the
// composite-build substitution vector:manager-ui). Holds the reusable, backend-agnostic Compose:
// the panel header, search field, ambience, theme seed, and the store HTML renderer.
plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

android {
    namespace = "org.matrix.vector.ui"
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
}
