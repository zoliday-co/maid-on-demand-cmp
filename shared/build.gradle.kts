import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "app.android.mainondemand.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        // commonTest also runs on a device (connectedAndroidDeviceTest); it needs the runner there.
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

kover {
    reports {
        filters {
            excludes {
                // Composable UI is not reachable from unit tests; coverage tracks the logic layers.
                packages(
                    "app.android.mainondemand.ui.screens",
                    "app.android.mainondemand.ui.components",
                    "app.android.mainondemand.ui.theme",
                    "mainondemand.shared.generated.resources",
                )
                classes(
                    "app.android.mainondemand.AppKt",
                    "app.android.mainondemand.ComposableSingletons*",
                    // Thin Android wrapper; needs a device, not a unit test.
                    "app.android.mainondemand.data.persistence.SharedPreferencesKeyValueStore",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
    }
}
