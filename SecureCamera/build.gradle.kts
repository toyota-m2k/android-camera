import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")        // for room compiler
//    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "io.github.toyota32k.secureCamera"
    compileSdk = 34

    signingConfigs {
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }

        val keyStorePath: String = properties.getProperty("key_store_path")!!
        val password: String = properties.getProperty("key_password")!!

        create("release") {
            storeFile = file(keyStorePath)
            storePassword = password
            keyAlias = "key0"
            keyPassword = password
        }
    }

    defaultConfig {
        applicationId = "io.github.toyota32k.secureCamera"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.7.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.preference.ktx)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

//    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation(libs.androidx.camera.core)
//    implementation("androidx.camera:camera-camera2:${camerax_version}")
//    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
//    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation(libs.androidx.camera.view)
//    implementation("androidx.camera:camera-extensions:${camerax_version}")

    implementation(libs.android.utilities)
    implementation(libs.android.binding)
    implementation(libs.android.viewex)
    implementation(libs.android.dialog)
    implementation(libs.android.media.processor)
    implementation(libs.android.server)
    implementation(libs.android.media.player)

    implementation(project(path=":libCamera"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}