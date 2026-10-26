plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.xenxo.wristai"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.xenxo.wristai"
        minSdk = 30
        targetSdk = 34
        versionCode = 200
        versionName = "2.0.0-watch2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly("com.google.android.wearable:wearable:2.9.0")
    testImplementation("junit:junit:4.13.2")
}
