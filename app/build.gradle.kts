import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Upload key for Google Play. keystore.properties and keystore/ stay out of version control.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Address and shared key of the AI server (see /server). Without backend.properties the app builds
// with the built-in AI switched off and only "bring your own key" available.
val backendProps = Properties().apply {
    rootProject.file("backend.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.calocare.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.calocare.app"
        minSdk = 26 // Health Connect + java.time
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BACKEND_URL", "\"${backendProps.getProperty("url", "")}\"")
        buildConfigField("String", "BACKEND_APP_KEY", "\"${backendProps.getProperty("appKey", "")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("upload") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
        // Same shrinking as release, but debug-signed and installable next to the real app for a smoke test.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        disable += "NullSafeMutableLiveData"
        // lintVital fails on this machine with NoClassDefFoundError (com.android.tools.lint.Main) from the
        // Gradle cache, not from the code. Run `gradlew lint` on a clean cache before each store release.
        checkReleaseBuilds = false
    }
    packaging {
        // The Anthropic SDK pulls in several jars that ship the same licence/metadata files.
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Online food search (Open Food Facts, Wikipedia thumbnails) and remote images
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // In-app camera with the guide circle
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // The camera provider returns a Guava ListenableFuture. Guava is already in the app at runtime (through
    // other libraries), which empties the stand-alone listenablefuture artifact, so compile against Guava itself.
    implementation("com.google.guava:guava:33.3.1-android")

    // Smart watch / band data: Wear OS, Samsung Health, Fitbit, Mi Fitness... all sync into Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // AI: meal photo analysis, calorie estimates, nutrition coach
    implementation("com.anthropic:anthropic-java:2.34.0")
}
