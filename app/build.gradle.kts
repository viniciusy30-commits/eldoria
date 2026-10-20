plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.eldoria.app"
    compileSdk = 34

    // versionCode vem do numero do workflow do GitHub (1 no build local)
    val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.eldoria.app"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
    }

    // Keystore fixo: NUNCA troque este arquivo, senao o app nao atualiza por cima
    signingConfigs {
        getByName("debug") {
            storeFile = file("eldoria.keystore")
            storeType = "pkcs12"
            storePassword = "eldoria2026"
            keyAlias = "eldoria"
            keyPassword = "eldoria2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
