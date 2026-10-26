plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.xenxo.wristai"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.xenxo.wristai"
        minSdk = 28          // Mi Watch (XMWT01) = Android 9 / Wear OS 2 China
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Para uso personal basta con firmar con la clave de debug.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Sin dependencias a propósito: solo el framework de Android (HTTP, JSON, audio y TTS nativos).
// Menos RAM, APK diminuto y nada que necesite Google Play Services.
dependencies {
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5")
}
