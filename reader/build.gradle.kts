plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
}

android {
    namespace = "kr.hebi.comicreader"

    defaultConfig {
        applicationId = "kr.hebi.comicreader"
        versionCode = 2
        versionName = "0.2.0-preview"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.documentfile:documentfile:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
