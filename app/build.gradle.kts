import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.beck.tirescanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beck.tirescanner"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "BARCODE_API_KEY", "\"${properties.getProperty("BARCODE_API_KEY")}\"")
        buildConfigField("String", "SYNC_TOKEN", "\"${properties.getProperty("SYNC_TOKEN")}\"")
    }

    buildFeatures {
        buildConfig = true
    }
    packagingOptions {
        resources.excludes += "META-INF/*"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)

    // HTTP API
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)

    // Image Loading from URL
    implementation(libs.glide)

    // Material Design Components
    implementation(libs.material)

    // HTTP server for device sync
    implementation(libs.nanohttpd)
}