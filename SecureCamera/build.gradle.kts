import java.util.Properties

plugins {
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.devtool.ksp)         // for room compiler
//    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "io.github.toyota32k.secureCamera"
    compileSdk = 35

    signingConfigs {
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }

        val keyStorePath: String = properties.getProperty("key_store_path") ?: ""
        val password: String = properties.getProperty("key_password") ?: ""

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
        targetSdk = 35
        versionCode = 1
        versionName = "1.18.5"

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
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.kotrinx.coroutines.core)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

//    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.view)

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