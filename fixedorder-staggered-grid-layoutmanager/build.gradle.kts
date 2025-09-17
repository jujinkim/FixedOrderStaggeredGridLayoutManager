plugins {
    id("com.android.library") version "8.5.2"
    kotlin("android") version "2.0.0"
}

android {
    namespace = "kim.jujin.fixedorder"
    compileSdk = (project.findProperty("COMPILE_SDK") as String).toInt()

    defaultConfig {
        minSdk = (project.findProperty("MIN_SDK") as String).toInt()
        targetSdk = (project.findProperty("TARGET_SDK") as String).toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(project.findProperty("JAVA_TOOLCHAIN") as String)
        targetCompatibility = JavaVersion.toVersion(project.findProperty("JAVA_TOOLCHAIN") as String)
    }
    kotlinOptions {
        jvmTarget = (project.findProperty("JAVA_TOOLCHAIN") as String)
        freeCompilerArgs += listOf("-Xjvm-default=all", "-Xcontext-receivers")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.5.0")
}
