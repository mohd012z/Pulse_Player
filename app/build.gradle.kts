plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Release signing is configured only when CI/local credentials are explicitly supplied.
    // Never embed a keystore or passwords in the repository.
    val pulseKeystore = System.getenv("PULSE_KEYSTORE_FILE")
    val pulseStorePassword = System.getenv("PULSE_KEYSTORE_PASSWORD")
    val pulseAlias = System.getenv("PULSE_KEY_ALIAS")
    val pulseKeyPassword = System.getenv("PULSE_KEY_PASSWORD")
    val pulseSigningReady = listOf(pulseKeystore, pulseStorePassword, pulseAlias, pulseKeyPassword)
        .all { !it.isNullOrBlank() }
    signingConfigs {
        if (pulseSigningReady) {
            create("pulseRelease") {
                storeFile = file(pulseKeystore!!)
                storePassword = pulseStorePassword
                keyAlias = pulseAlias
                keyPassword = pulseKeyPassword
            }
        }
    }

    namespace = "com.pulseplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pulseplayer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "4.0.0"
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    buildTypes {
        release {
            if (pulseSigningReady) signingConfig = signingConfigs.getByName("pulseRelease")
            isMinifyEnabled = false
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
    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.google.guava:guava:33.4.8-android")
    testImplementation("junit:junit:4.13.2")
}