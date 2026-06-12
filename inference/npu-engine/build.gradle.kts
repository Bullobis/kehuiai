plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.kehuiai.inference.npu"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        targetSdk = 34
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        prefabPublishing = true
    }

    prefab {
        create("npu_engine") {
            headers = "src/main/cpp/include"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
