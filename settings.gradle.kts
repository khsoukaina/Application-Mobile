pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.0.2" apply false
        id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}


rootProject.name = "intelligentcameraapp"
include(":app")
include(":pytorch_android")
include(":pytorch_android_torchvision")


