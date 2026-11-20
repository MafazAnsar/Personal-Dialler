plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mafaz.dialer"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            // The keystore and passwords come from CI secrets (see README). Without them the
            // standard local debug key is used, so a plain local build still works.
            val storePw = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
            val keyPw = providers.gradleProperty("KEY_PASSWORD").orNull
            val alias = providers.gradleProperty("KEY_ALIAS").orNull
            val ks = file("debug.keystore")
            if (storePw != null && keyPw != null && alias != null && ks.exists()) {
                storeFile = ks
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
            }
        }
    }

    defaultConfig {
        applicationId = "com.mafaz.dialer"
        minSdk = 29
        targetSdk = 35
        // CI numbers every build, so each new build is always newer than the one before it.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (run != null) 100 + run else 21
        versionName = if (run != null) "0.20 (build $run)" else "0.20"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Test-only: not part of the APK.
    testImplementation("junit:junit:4.13.2")
}
