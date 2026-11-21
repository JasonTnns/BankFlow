import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}
// ---- Release settings. Read from ~/.gradle/gradle.properties or environment variables, never the repository.
fun secret(property: String, environment: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(environment)).orNull

val bankflowKeystorePath = secret("bankflow.keystore.path", "BANKFLOW_KEYSTORE_PATH")
val bankflowKeystorePassword = secret("bankflow.keystore.password", "BANKFLOW_KEYSTORE_PASSWORD")
val bankflowKeyAlias = secret("bankflow.key.alias", "BANKFLOW_KEY_ALIAS")
val bankflowKeyPassword = secret("bankflow.key.password", "BANKFLOW_KEY_PASSWORD")
val releaseSigningReady = listOf(bankflowKeystorePath, bankflowKeystorePassword, bankflowKeyAlias, bankflowKeyPassword)
    .all { !it.isNullOrBlank() }

val bankflowApiBaseUrl: String =
    providers.gradleProperty("bankflow.apiBaseUrl").getOrElse("https://api.bankflow.example/api/v1/")
val bankflowVersionName: String = providers.gradleProperty("bankflow.versionName").getOrElse("1.0")
val bankflowVersionCode: Int = providers.gradleProperty("bankflow.versionCode").orNull?.toIntOrNull() ?: 1

// A release build must never go out unsigned or pointed at the placeholder server.
gradle.taskGraph.whenReady {
    if (hasTask(":app:assembleRelease") || hasTask(":app:bundleRelease")) {
        val problems = buildList {
            if (!bankflowApiBaseUrl.startsWith("https://")) add("bankflow.apiBaseUrl must start with https://")
            if (!bankflowApiBaseUrl.endsWith("/")) add("bankflow.apiBaseUrl must end with /")
            if (bankflowApiBaseUrl.contains("bankflow.example")) add("bankflow.apiBaseUrl is still the placeholder")
            if (!releaseSigningReady) add("signing is not configured (bankflow.keystore.*, bankflow.key.*)")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Release build refused:\n - " + problems.joinToString("\n - "))
        }
    }
}
android {
    namespace = "com.example.bankflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bankflow"
        minSdk = 26
        targetSdk = 35
        versionCode = bankflowVersionCode
        versionName = bankflowVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(bankflowKeystorePath!!)
                storePassword = bankflowKeystorePassword
                keyAlias = bankflowKeyAlias
                keyPassword = bankflowKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/api/v1/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$bankflowApiBaseUrl\"")
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
        create("minified") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
baselineProfile {
    dexLayoutOptimization = true
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType == "benchmarkRelease" || variant.buildType == "nonMinifiedRelease") {
            variant.buildConfigFields?.put(
                "API_BASE_URL",
                BuildConfigField("String", "\"http://10.0.2.2:3000/api/v1/\"", "local server for profiling"),
            )
        }
    }
}
composeCompiler {
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {

    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.ui)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.biometric)


    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines + serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Unit tests (src/test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    // Instrumented / UI tests (src/androidTest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.room.testing)
}