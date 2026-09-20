plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "net.qiujuer.lame"
    compileSdk = 37

    defaultConfig {
        minSdk = 28

        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++11", "-O3")
                cFlags += listOf(
                    "-O3",
                    "-ffast-math",
                    "-funroll-loops",
                    "-Wno-error=implicit-function-declaration"
                )
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
