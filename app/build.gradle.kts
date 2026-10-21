plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.jlucraft.manager"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.jlucraft.manager"
        minSdk = 33
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 2
        versionName = "0.2.0"
    }
    signingConfigs {
        create("production") {
            val privateDirectory = providers.environmentVariable("JLU_SIGNING_DIR").orNull
            if (privateDirectory != null) {
                storeFile = file("$privateDirectory/android-release.p12")
                storePassword = file("$privateDirectory/android-store-password").readText().trim()
                keyAlias = "jlucraft-manager"
                keyPassword = storePassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.all { it.maxHeapSize = "512m"; it.maxParallelForks = 1 } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}
dependencies {
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.unifiedpush.android:connector:3.3.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
// Explicit failure prevents shipping a manager that only fails when JNI is first used.
tasks.register("checkNativeLibrary") {
    doLast {
        check(fileTree("src/main/jniLibs").matching { include("**/libjlucraft_manager.so") }.files.isNotEmpty()) {
            "Build JNI first: ./scripts/build-native.sh"
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("checkNativeLibrary") }

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst { check(providers.environmentVariable("JLU_SIGNING_DIR").isPresent) { "Set JLU_SIGNING_DIR to the private signing directory" } }
}
