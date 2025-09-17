plugins {
    id("com.android.library") version "8.5.2"
    kotlin("android") version "2.0.0"
    `maven-publish`
    signing
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

    // Enable variant publishing with sources/javadoc jars
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.5.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("mavenRelease") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "fixedorder-staggered-grid-layoutmanager"
                version = project.version.toString()

                pom {
                    name.set("FixedOrderStaggeredGridLayoutManager")
                    description.set(
                        project.findProperty("POM_DESCRIPTION") as String?
                            ?: "Deterministic, fixed-order staggered grid LayoutManager for RecyclerView."
                    )
                    url.set(project.findProperty("POM_URL") as String? ?: "https://github.com/jujinkim/FixedOrderStaggeredGridLayoutManager")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set(project.findProperty("POM_DEVELOPER_ID") as String? ?: "jujinkim")
                            name.set(project.findProperty("POM_DEVELOPER_NAME") as String? ?: "Jujin Kim")
                            url.set(project.findProperty("POM_DEVELOPER_URL") as String? ?: "https://github.com/jujinkim")
                        }
                    }
                    scm {
                        url.set("https://github.com/jujinkim/FixedOrderStaggeredGridLayoutManager")
                        connection.set("scm:git:git://github.com/jujinkim/FixedOrderStaggeredGridLayoutManager.git")
                        developerConnection.set("scm:git:ssh://git@github.com/jujinkim/FixedOrderStaggeredGridLayoutManager.git")
                    }
                }
            }
        }

        repositories {
            // Configure OSSRH (Sonatype) repositories. Defaults to s01 host.
            maven {
                val releasesRepoUrl = uri(project.findProperty("OSSRH_RELEASES_URL") as String?
                    ?: "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri(project.findProperty("OSSRH_SNAPSHOTS_URL") as String?
                    ?: "https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if ((project.version as String).endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                credentials {
                    username = (project.findProperty("OSSRH_USERNAME") as String?) ?: System.getenv("OSSRH_USERNAME")
                    password = (project.findProperty("OSSRH_PASSWORD") as String?) ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }
}

signing {
    // Use in-memory PGP keys if provided; otherwise skip signing for local builds
    val signingKey = (project.findProperty("SIGNING_KEY") as String?) ?: System.getenv("SIGNING_KEY")
    val signingPass = (project.findProperty("SIGNING_PASSWORD") as String?) ?: System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank() && !signingPass.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPass)
        sign(publishing.publications)
    } else {
        logger.lifecycle("Signing: SKIPPED (no SIGNING_KEY provided)")
    }
}
