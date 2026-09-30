plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aess.gymflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aess.gymflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.5"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildFeatures { compose = true }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".test"
            manifestPlaceholders["appLabel"] = "GymGlow Test"
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
