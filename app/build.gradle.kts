import java.util.Properties
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.aidhunttrac.cedlir"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "1.0.7"

    buildConfigField("boolean", "DEVICE_TEST", "false")
    buildConfigField("boolean", "LIVE_TEST", "false")
    buildConfigField("boolean", "PHYSICAL_TEST", "false")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release credentials stay outside source; debug/test variants use Android's debug key.
  signingConfigs {
    create("release") {
      val credentialsFile = rootProject.file(
        providers.gradleProperty("releaseSigningProperties").orElse("keystore.properties").get()
      )
      if (credentialsFile.isFile) {
        val credentials = Properties().apply { credentialsFile.inputStream().use { load(it) } }
        storeFile = credentials.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
        storePassword = credentials.getProperty("storePassword")
        keyAlias = credentials.getProperty("keyAlias")
        keyPassword = credentials.getProperty("keyPassword")
      }
    }
  }

  val releaseSigning = signingConfigs.getByName("release")
  val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
    doLast {
      check(releaseSigning.storeFile?.isFile == true &&
        !releaseSigning.storePassword.isNullOrBlank() &&
        !releaseSigning.keyAlias.isNullOrBlank() &&
        !releaseSigning.keyPassword.isNullOrBlank()) {
        "Release signing credentials are required. Configure protected keystore.properties or -PreleaseSigningProperties; no fallback is permitted."
      }
    }
  }
  tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle")) {
      dependsOn(validateReleaseCredentials)
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      enableUnitTestCoverage = providers.gradleProperty("ciCoverage").orNull == "true"
    }
    create("deviceTest") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".devicetest"
      versionNameSuffix = "-emulator"
      matchingFallbacks += listOf("debug")
      buildConfigField("boolean", "DEVICE_TEST", "true")
    }
    create("liveTest") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".livetest"
      versionNameSuffix = "-live-test"
      matchingFallbacks += listOf("debug")
      buildConfigField("boolean", "LIVE_TEST", "true")
    }
    create("physicalTest") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".physicaltest"
      versionNameSuffix = "-physical-test"
      matchingFallbacks += listOf("debug")
      buildConfigField("boolean", "PHYSICAL_TEST", "true")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }


  buildFeatures {
    compose = true
    buildConfig = true
  }
  testBuildType = if (providers.gradleProperty("deviceTest").orNull == "true") "deviceTest" else "debug"
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("FIREBASE_API_KEY")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}



tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"))
    }
}
