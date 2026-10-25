plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "br.com.camaleao.smsprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "br.com.camaleao.smsprobe"
        minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
