plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.toyota32k.monitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.toyota32k.monitor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.camera.core)
//    implementation("androidx.camera:camera-camera2:${camerax_version}")
//    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
//    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation(libs.androidx.camera.view)
//    implementation("androidx.camera:camera-extensions:${camerax_version}")

    implementation(libs.android.utilities)
    implementation(libs.android.binding)
    implementation(libs.android.dialog)
    implementation(project(path=":libCamera"))


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}