import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Trakt API credentials are read (in priority order) from:
//   1. a Gradle property  -Ptrakt.clientId=...  /  -Ptrakt.clientSecret=...
//   2. gradle.properties   trakt.clientId=...     trakt.clientSecret=...
//   3. local.properties    trakt.clientId=...     trakt.clientSecret=...
//   4. environment vars    TRAKT_CLIENT_ID        TRAKT_CLIENT_SECRET
// They are NEVER committed. Create an app at https://trakt.tv/oauth/applications/new
// (redirect uri: urn:ietf:wg:oauth:2.0:oob) and paste the values into local.properties.
fun traktSecret(prop: String, env: String): String {
    (project.findProperty(prop) as String?)?.let { if (it.isNotBlank()) return it }
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        val p = Properties().apply { local.inputStream().use { load(it) } }
        p.getProperty(prop)?.let { if (it.isNotBlank()) return it }
    }
    System.getenv(env)?.let { if (it.isNotBlank()) return it }
    return ""
}

android {
    namespace = "com.trakt.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trakt.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${traktSecret("trakt.clientId", "TRAKT_CLIENT_ID")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${traktSecret("trakt.clientSecret", "TRAKT_CLIENT_SECRET")}\"")

        // Optional: TMDB v3 API key enables "Available on <service>" (JustWatch data).
        // Get a free key at https://www.themoviedb.org/settings/api (Developer plan).
        buildConfigField("String", "TMDB_API_KEY", "\"${traktSecret("tmdb.apiKey", "TMDB_API_KEY")}\"")
        buildConfigField(
            "String",
            "TMDB_REGION",
            "\"${(project.findProperty("tmdb.region") as String? ?: System.getenv("TMDB_REGION") ?: "US")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Compose for TV
    implementation(libs.androidx.tv.material)
    // Compose Material3 is used alongside tv-material3 for a few widgets that
    // tv-material3 doesn't ship (progress indicator, text field for search).
    implementation(libs.androidx.material3)

    // Networking + JSON + images
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    implementation(libs.androidx.datastore.preferences)
}
