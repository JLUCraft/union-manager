plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val protobufVersion = "4.35.0-RC2"
val protoc by configurations.creating

val protoGeneratedJava = layout.buildDirectory.dir("generated/source/proto/main/java")
val protoGeneratedKotlin = layout.buildDirectory.dir("generated/source/proto/main/kotlin")
val protoSharedDir = layout.projectDirectory.dir("../../docs/proto")

android {
    namespace = "com.jlucraft.console"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jlucraft.console"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin { jvmToolchain(21) }

    packaging {
        resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(protoGeneratedJava.get().asFile)
            java.srcDir(protoGeneratedKotlin.get().asFile)
        }
    }
}

val generateProto by tasks.registering(Exec::class) {
    notCompatibleWithConfigurationCache("protoc configuration references are not serializable")

    val javaDir = protoGeneratedJava.get().asFile
    val kotlinDir = protoGeneratedKotlin.get().asFile
    val protoFiles = fileTree(protoSharedDir) { include("*.proto") }

    inputs.files(protoFiles)
    outputs.dir(javaDir)
    outputs.dir(kotlinDir)

    doFirst {
        check(protoSharedDir.asFile.isDirectory) {
            "Shared protobuf directory missing: ${protoSharedDir.asFile.absolutePath}"
        }
        javaDir.mkdirs()
        kotlinDir.mkdirs()
        protoc.singleFile.setExecutable(true)
    }

    commandLine(
        listOf(
            protoc.singleFile.absolutePath,
            "--proto_path=${protoSharedDir.asFile.absolutePath}",
            "--java_out=lite:${javaDir.absolutePath}",
            "--kotlin_out=lite:${kotlinDir.absolutePath}",
        ) + protoFiles.files.sortedBy { it.name }.map { it.absolutePath }
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(generateProto)
}

configurations.all {
    exclude("com.google.crypto.tink", "tink-android")
    exclude("com.google.protobuf", "protobuf-java")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.1")
    implementation("androidx.navigation3:navigation3-ui:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.startup:startup-runtime:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.protobuf:protobuf-kotlin-lite:$protobufVersion")
    protoc("com.google.protobuf:protoc:$protobufVersion:windows-x86_64@exe")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    implementation("org.unifiedpush.android:connector:3.3.2")
    implementation("org.unifiedpush.android:embedded-fcm-distributor:3.0.0") {
        exclude("com.google.crypto.tink", "tink-android")
    }

    implementation("io.libp2p:jvm-libp2p:1.2.2-RELEASE")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}
