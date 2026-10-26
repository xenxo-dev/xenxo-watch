plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.xenxo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.xenxo.launcher"
        minSdk = 28          // Mi Watch XMWT01 = Android 9 / Wear OS 2 China
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

// Solo framework Android, salvo la única excepción de CLAUDE.md: la librería wearable support de Google
// para las esferas (CanvasWatchFaceService y complicaciones). No depende de Google Play Services.
// Sin sus dependencias transitivas (appcompat, preference, fragment, recyclerview…): las clases que usamos
// (WatchFaceService y complicaciones) no las necesitan y así no engordan el APK ni la RAM.
dependencies {
    implementation("com.google.android.support:wearable:2.9.0") { isTransitive = false }
    // Solo por sus atributos XML (layout_marginTopPercent…), que usan los estilos de la librería wearable
    implementation("androidx.percentlayout:percentlayout:1.0.0") { isTransitive = false }
    compileOnly("com.google.android.wearable:wearable:2.9.0")
}
