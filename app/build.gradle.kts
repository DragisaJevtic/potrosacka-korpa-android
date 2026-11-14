plugins {
    id("com.android.application") version "9.4.1"
}

val releaseUrl = providers.gradleProperty("korpaApiUrl").orElse("").get().trim().trimEnd('/')
val debugUrl = providers.gradleProperty("korpaDebugApiUrl").orElse("http://10.0.2.2:8080").get().trim().trimEnd('/')
require(releaseUrl.isEmpty() || releaseUrl.startsWith("https://")) {
    "korpaApiUrl mora da koristi HTTPS."
}

android {
    namespace = "dev.intelisystems.potrosackakorpa"
    compileSdk = 36

    defaultConfig {
        applicationId = providers.gradleProperty("korpaApplicationId").orElse("dev.intelisystems.potrosackakorpa").get()
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.3"
    }

    buildFeatures { buildConfig = true }
    androidResources { noCompress += "gzip" }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"$debugUrl\"")
        }
        release {
            buildConfigField("String", "API_URL", "\"$releaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.9.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}
