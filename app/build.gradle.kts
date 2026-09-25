plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mariya.modelviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mariya.modelviewer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Filament dependencies
    implementation("com.google.android.filament:filament-android:1.53.4")
    implementation("com.google.android.filament:gltfio-android:1.53.4")
    implementation("com.google.android.filament:filament-utils-android:1.53.4")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
