plugins {
    id("com.android.application") version "9.4.1"
}

val releaseUrl = providers.gradleProperty("korpaApiUrl").orElse("").get().trim().trimEnd('/')
val debugUrl = providers.gradleProperty("korpaDebugApiUrl").orElse("http://10.0.2.2:8080").get().trim().trimEnd('/')
require(releaseUrl.isEmpty() || releaseUrl.startsWith("https://")) {
    "korpaApiUrl mora da koristi HTTPS."
}

val adsAppId = providers.gradleProperty("korpaAdMobAppId").orElse("").get().trim()
val adsUnitId = providers.gradleProperty("korpaAdMobBannerId").orElse("").get().trim()
require(adsAppId.isEmpty() || adsAppId.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")))
require(adsUnitId.isEmpty() || adsUnitId.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")))
val interstitialId = providers.gradleProperty("korpaAdMobInterstitialId").orElse("").get().trim()
require(interstitialId.isEmpty() || interstitialId.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")))
val liveAds = adsAppId.isNotEmpty() && adsUnitId.isNotEmpty()

android {
    namespace = "dev.intelisystems.potrosackakorpa"
    compileSdk = 36

    defaultConfig {
        applicationId = providers.gradleProperty("korpaApplicationId").orElse("dev.intelisystems.potrosackakorpa").get()
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "0.5.4"
    }

    buildFeatures { buildConfig = true }
    androidResources { noCompress += "gzip" }

    buildTypes {
        debug {
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "AD_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "API_URL", "\"$debugUrl\"")
        }
        release {
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"${if (liveAds) interstitialId else ""}\"")
            manifestPlaceholders["adMobAppId"] = if (liveAds) adsAppId else "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "AD_BANNER_ID", "\"${if (liveAds) adsUnitId else ""}\"")
            buildConfigField("String", "API_URL", "\"$releaseUrl\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("androidx.activity:activity:1.9.3")
    // Ads brings an older Fragment transitively; ActivityResult requires >= 1.3.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
