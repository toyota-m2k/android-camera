import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

configure<ApplicationExtension> {
    namespace = "io.github.toyota32k.monitor"
    compileSdk = 37

    signingConfigs {
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }

        val keyStorePath: String? = properties.getProperty("key_store_path")
        val password: String? = properties.getProperty("key_password")

        if(keyStorePath!=null) {
            create("release") {
                storeFile = file(keyStorePath)
                storePassword = password
                keyAlias = "key0"
                keyPassword = password
            }
        }
    }
    defaultConfig {
        applicationId = "io.github.toyota32k.monitor"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val sign = signingConfigs.findByName("release")
            if(sign!=null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            val sign = signingConfigs.findByName("release")
            if(sign!=null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
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
    implementation(libs.androidx.camera.view)

    implementation(libs.android.utilities)
    implementation(libs.android.binding)
    implementation(libs.android.dialog)
    implementation(project(path=":libCamera"))


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}