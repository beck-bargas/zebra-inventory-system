import java.util.Properties

plugins {
    id("com.android.application")
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)

    // HTTP API
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}