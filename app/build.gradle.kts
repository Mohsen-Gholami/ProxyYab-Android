plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.proxyyab.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.proxyyab.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.2"
    }
    buildFeatures { viewBinding = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
