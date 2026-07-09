plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.engagepop"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // FCM token + message delivery. The app supplies google-services.json.
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json on the JVM for unit tests
}

// Published to Maven Central as com.engagepop:engagepop-android.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.engagepop"
            artifactId = "engagepop-android"
            version = "0.1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
