plugins {
    id("com.android.application") version "8.5.2"
    kotlin("android") version "2.0.0"
}

android {
    namespace = "kim.jujin.fixedorder.sample"
    compileSdk = (project.findProperty("COMPILE_SDK") as String).toInt()

    defaultConfig {
        applicationId = "kim.jujin.fixedorder.sample"
        minSdk = (project.findProperty("MIN_SDK") as String).toInt()
        targetSdk = (project.findProperty("TARGET_SDK") as String).toInt()
        versionCode = 1
        versionName = project.findProperty("VERSION_NAME") as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.toVersion(project.findProperty("JAVA_TOOLCHAIN") as String)
        targetCompatibility = JavaVersion.toVersion(project.findProperty("JAVA_TOOLCHAIN") as String)
    }
    kotlinOptions {
        jvmTarget = (project.findProperty("JAVA_TOOLCHAIN") as String)
    }
}

dependencies {
    implementation(project(":fixedorder-staggered-grid-layoutmanager"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
