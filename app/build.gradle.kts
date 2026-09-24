plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

// Backend address from gradle.properties (budjet.apiBaseUrl). Empty = no server.
val apiBaseUrl = (project.findProperty("budjet.apiBaseUrl") as String?).orEmpty().trim().trimEnd('/')

android {
    namespace = "com.abe.bud_jet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abe.bud_jet"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // Android 9+ blocks plain http unless allowed: allowed only when the backend URL is http://.
        manifestPlaceholders["usesCleartextTraffic"] = apiBaseUrl.startsWith("http://").toString()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kapt {
    arguments {
        // Exported Room schemas are needed to write and verify migrations.
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    // enableEdgeToEdge() for Android 15+ edge-to-edge display.
    implementation(libs.androidx.activity.ktx)
    // Premium subscription (Google Play Billing).
    implementation(libs.billing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    kapt("androidx.room:room-compiler:2.8.4")
}