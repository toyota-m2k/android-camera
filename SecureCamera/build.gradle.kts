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
        versionName = "1.7.3"

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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

//    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    val camerax_version = "1.3.3"
    implementation("androidx.camera:camera-core:${camerax_version}")
//    implementation("androidx.camera:camera-camera2:${camerax_version}")
//    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
//    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
//    implementation("androidx.camera:camera-extensions:${camerax_version}")

    implementation("com.github.toyota-m2k:android-utilities:2.0.2")
    implementation("com.github.toyota-m2k:android-binding:1.1.3")
    implementation("com.github.toyota-m2k:android-viewex:1.0.7")
    implementation("com.github.toyota-m2k:android-dialog:3.7.2")
    implementation("com.github.toyota-m2k:android-media-processor:2.7.2")
    implementation("com.github.toyota-m2k:android-server:1.0.1")
    implementation("com.github.toyota-m2k:android-media-player:1.0.1")

    implementation(project(path=":libCamera"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}