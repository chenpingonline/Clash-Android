import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val distributionRelease = providers.gradleProperty("distributionRelease").orNull == "true"
val localReleaseSigning = providers.gradleProperty("localReleaseSigning").orNull == "true"
check(!(distributionRelease && localReleaseSigning)) { "Choose distribution signing or local debug signing, not both." }
val distributionSigning = Properties().apply {
    if (distributionRelease) {
        val configPath = providers.environmentVariable("CLASH_RELEASE_SIGNING_FILE").orNull
            ?: error("Set CLASH_RELEASE_SIGNING_FILE to a private signing.properties file outside the repository.")
        file(configPath).inputStream().use { load(it) }
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
            check(!getProperty(key).isNullOrBlank()) { "Missing release signing property: $key" }
        }
    }
}

android {
    namespace = "cn.nascp.clash"
    compileSdk = 36
    defaultConfig {
        applicationId = if (distributionRelease) "cn.chenpingonline.clash" else "cn.nascp.clash.dev"
        minSdk = 29
        targetSdk = 36
        versionCode = 51
        versionName = if (distributionRelease) "0.3.1" else "0.3.1-dev"
        manifestPlaceholders["appLabel"] = if (distributionRelease) "Clash" else "Clash Dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (distributionRelease) {
            create("distribution") {
                storeFile = file(distributionSigning.getProperty("storeFile"))
                storePassword = distributionSigning.getProperty("storePassword")
                keyAlias = distributionSigning.getProperty("keyAlias")
                keyPassword = distributionSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (distributionRelease) {
                signingConfig = signingConfigs.getByName("distribution")
            } else if (localReleaseSigning) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = true; resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val verifyNativeCore by tasks.registering {
    doLast {
        check(file("src/main/jniLibs/arm64-v8a/libclashcore.so").exists()) {
            "Missing Mihomo JNI library. Run ./scripts/build-debug.sh from the repository root."
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyNativeCore) }
