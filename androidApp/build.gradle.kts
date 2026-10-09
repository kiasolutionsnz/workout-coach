import java.net.URI
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "tech.kiasolutions.workoutcoach"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.kiasolutions.workoutcoach"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        val endpoint = providers.environmentVariable("WORKOUT_API_BASE_URL").getOrElse("https://api.example.invalid")
        val uri = URI(endpoint)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/")) { "API endpoint must be an HTTPS origin without credentials" }
        buildConfigField("String", "WORKOUT_API_BASE_URL", "\"${endpoint.trimEnd('/')}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mediapipe:tasks-vision:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
