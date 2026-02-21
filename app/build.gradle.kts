plugins {
    alias(libs.plugins.android.application)
}

android {

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    namespace = "com.example.myapplication"
    compileSdk = 36


    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.1.0")
    implementation("androidx.annotation:annotation:1.8.0") // Or the latest version
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.mappls.sdk:mappls-android-sdk:8.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
