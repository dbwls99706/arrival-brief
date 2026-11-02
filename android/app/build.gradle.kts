plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 useLibrary("android.car")
 namespace = "studio.ax.arrival"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "studio.ax.arrival"; minSdk = 28; targetSdk = 35; versionCode = 11; versionName = "1.0.0-rc2"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions.unitTests.all {
  it.systemProperty("arrival.decisionCases", rootProject.file("../tests/decision-cases.tsv").absolutePath)
 }
 val releaseStore = providers.environmentVariable("ARRIVAL_RELEASE_KEYSTORE").orNull
 val releasePassword = providers.environmentVariable("ARRIVAL_RELEASE_PASSWORD").orNull
 if (!releaseStore.isNullOrBlank() && !releasePassword.isNullOrBlank()) {
  signingConfigs.create("submission") {
   storeFile = file(releaseStore)
   storePassword = releasePassword
   keyAlias = "arrival-release"
   keyPassword = releasePassword
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   if (signingConfigs.findByName("submission") != null) signingConfig = signingConfigs.getByName("submission")
  }
 }
}

dependencies {
 implementation("ai.pleos.playground:NaviHelper:3.0.5")
 implementation("com.google.zxing:core:3.5.4")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:core:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
