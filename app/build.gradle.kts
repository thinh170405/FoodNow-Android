import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val serviceConfig = Properties().apply {
    val source = rootProject.file("services.properties")
    if (source.exists()) source.inputStream().use { load(it) }
}
val firebaseFile = file("google-services.json")
val firebaseJson = if (firebaseFile.exists()) groovy.json.JsonSlurper().parse(firebaseFile) as Map<*, *> else emptyMap<Any, Any>()
val projectInfo = firebaseJson["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val client = (firebaseJson["client"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull {
    val info=it["client_info"] as? Map<*, *>
    (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.example.project1"
}
fun setting(key: String) = serviceConfig.getProperty(key, "")

android {
    buildFeatures { resValues = true }
    namespace = "com.example.project1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.project1"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "google_maps_key", setting("MAPS_API_KEY"))
        resValue("string", "google_app_id", ((client?.get("client_info") as? Map<*, *>)?.get("mobilesdk_app_id") ?: "").toString())
        resValue("string", "google_api_key", (((client?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key") ?: "").toString())
        resValue("string", "gcm_defaultSenderId", (projectInfo["project_number"] ?: "").toString())
        resValue("string", "project_id", (projectInfo["project_id"] ?: "").toString())
        resValue("string", "default_web_client_id", (((client?.get("oauth_client") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 })?.get("client_id") ?: "").toString())
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
