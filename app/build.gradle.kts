plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val tvPieBuild = providers.gradleProperty("tvPieBuild").orNull == "true"

android {
    namespace = "com.pulseplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pulseplayer.app"
        minSdk = if (tvPieBuild) 28 else 26
        targetSdk = 36
        versionCode = 4
        versionName = if (tvPieBuild) "4.0.0-tv-pie" else "4.0.0"
        if (tvPieBuild) {
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
    }

    buildTypes {
        release {
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
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    testImplementation("junit:junit:4.13.2")
}