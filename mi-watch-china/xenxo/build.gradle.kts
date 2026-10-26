plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.xenxo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.xenxo"
        minSdk = 28          // Xiaomi Mi Watch (XMWT01) = Android 9 / Wear OS 2 China
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Cero dependencias en el APK: solo el framework nativo de Android (Views clásicas, SQLite, BroadcastReceivers).
dependencies {
    // Modo ambiente del marcador de pádel (WearableActivityController). compileOnly: la clase la pone el
    // propio reloj como librería del sistema (<uses-library com.google.android.wearable>), no va en el APK.
    compileOnly("com.google.android.wearable:wearable:2.9.0")
    // Sincronización con la app del móvil por el Data Layer de Wear OS (io.xenxo.sync). Excepción a "cero
    // dependencias" decidida por el usuario el 2026-10-21; validada con Wear OS China (xenxo-phone/XENXO.md).
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    // Solo para los tests en el PC (reglas del pádel); no entra en el APK
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
