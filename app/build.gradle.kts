plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.matthew.perfoverlay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matthew.perfoverlay"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Sign release with the debug key so assembleRelease works out of the
        // box for on-device profiling. Override via env for store builds.
        create("releaseLocal") {
            val keystoreFile = System.getenv("PERFOVERLAY_KEYSTORE")?.let { file(it) }
            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("PERFOVERLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PERFOVERLAY_KEY_ALIAS")
                keyPassword = System.getenv("PERFOVERLAY_KEY_PASSWORD")
            } else {
                val debugKey = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storeFile = debugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("releaseLocal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PerfOverlay-${versionName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
