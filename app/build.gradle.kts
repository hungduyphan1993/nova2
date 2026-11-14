import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration.FilterType
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

fun parseDotEnvContent(rawText: String?): Map<String, String> {
  if (rawText.isNullOrBlank()) return emptyMap()
  val result = linkedMapOf<String, String>()
  rawText.lineSequence().forEach { rawLine ->
    val line = rawLine.trim()
    if (line.isEmpty() || line.startsWith("#")) return@forEach
    val cleanLine = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
    val eqIdx = cleanLine.indexOf('=')
    if (eqIdx <= 0) return@forEach
    val key = cleanLine.substring(0, eqIdx).trim()
    var value = cleanLine.substring(eqIdx + 1).trim()
    if (value.length >= 2 &&
      ((value.startsWith("\"") && value.endsWith("\"")) ||
        (value.startsWith("'") && value.endsWith("'")))
    ) {
      value = value.substring(1, value.length - 1)
    }
    result[key] = value
  }
  return result
}

fun readTrackedFileText(regularFile: RegularFile): String? {
  return runCatching { providers.fileContents(regularFile).asText.orNull }.getOrNull()
}

val defaultEnvMap: Map<String, String> =
  parseDotEnvContent(readTrackedFileText(rootProject.layout.projectDirectory.file(".env.example"))) +
    parseDotEnvContent(readTrackedFileText(layout.projectDirectory.file(".env.example")))
val localEnvMap: Map<String, String> =
  parseDotEnvContent(readTrackedFileText(rootProject.layout.projectDirectory.file(".env"))) +
    parseDotEnvContent(readTrackedFileText(layout.projectDirectory.file(".env")))

fun isMissingOrPlaceholder(raw: String?, keys: Array<out String>): Boolean {
  val trimmed = raw?.trim() ?: return true
  if (trimmed.isEmpty()) return true
  if (keys.any { k -> trimmed.equals(k, ignoreCase = true) }) return true
  val upper = trimmed.uppercase()
  if (upper == "NULL" || upper == "UNDEFINED" || upper == "NONE" || upper == "N/A") return true
  if (trimmed.startsWith("<") && trimmed.endsWith(">")) return true
  if (upper.startsWith("YOUR_") || upper.startsWith("MY_") || upper.endsWith("_DEFAULT_VALUE")) return true
  return false
}

fun resolveWhitelabelEnv(vararg keys: String, default: String = ""): String {
  for (key in keys) {
    val fromDotEnv = localEnvMap[key]
    if (!isMissingOrPlaceholder(fromDotEnv, keys)) return fromDotEnv!!.trim()
  }
  for (key in keys) {
    val fromSysEnv = runCatching { providers.environmentVariable(key).orNull }.getOrNull()
    if (!isMissingOrPlaceholder(fromSysEnv, keys)) return fromSysEnv!!.trim()
  }
  for (key in keys) {
    val fromGradleProp = providers.gradleProperty(key).orNull
    if (!isMissingOrPlaceholder(fromGradleProp, keys)) return fromGradleProp!!.trim()
  }
  for (key in keys) {
    val fromExample = defaultEnvMap[key]
    if (!isMissingOrPlaceholder(fromExample, keys)) return fromExample!!.trim()
  }
  return default
}

fun asBuildConfigString(raw: String): String {
  val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
  return "\"$escaped\""
}

val defaultApplicationId = "com.aistudio.novastream.qrxvkp"
val packageIdRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
val rawApplicationId = resolveWhitelabelEnv(
  "WHITELABEL_APPLICATION_ID",
  "APPLICATION_ID",
  default = defaultApplicationId
)
val resolvedApplicationId = if (packageIdRegex.matches(rawApplicationId)) {
  rawApplicationId
} else {
  logger.warn("Ignoring invalid WHITELABEL_APPLICATION_ID '$rawApplicationId'; falling back to $defaultApplicationId")
  defaultApplicationId
}

val resolvedLauncherLabel = resolveWhitelabelEnv(
  "WHITELABEL_LAUNCHER_LABEL",
  "LAUNCHER_LABEL",
  default = ""
)

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = resolvedApplicationId
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["appLabel"] = resolvedLauncherLabel.ifBlank { "@string/app_name" }

    buildConfigField(
      "String",
      "WHITELABEL_APP_NAME",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_APP_NAME", "APP_NAME", default = "NovaStream Player"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_LAUNCHER_LABEL",
      asBuildConfigString(resolvedLauncherLabel.ifBlank { "NovaStream" })
    )
    buildConfigField(
      "String",
      "WHITELABEL_TAGLINE",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_TAGLINE", "APP_TAGLINE", default = "Xtream Codes • M3U/M3U8 • XMLTV EPG"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_LOGO_URL",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_LOGO_URL", "BRAND_LOGO_URL", default = ""))
    )
    buildConfigField(
      "String",
      "WHITELABEL_PRIMARY_COLOR",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_PRIMARY_COLOR", "PRIMARY_COLOR", default = "#00E5FF"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_SECONDARY_COLOR",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_SECONDARY_COLOR", "SECONDARY_COLOR", default = "#8C52FF"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_ACCENT_COLOR",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_ACCENT_COLOR", "ACCENT_COLOR", default = "#FFB800"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_BG_COLOR",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_BG_COLOR", "BG_COLOR", default = "#06080D"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_SUPPORT_TEXT",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_SUPPORT_TEXT", "SUPPORT_TEXT", default = ""))
    )
    buildConfigField(
      "String",
      "WHITELABEL_SUPPORT_URL",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_SUPPORT_URL", "SUPPORT_URL", default = ""))
    )
    buildConfigField(
      "String",
      "WHITELABEL_API_SERVER",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_API_SERVER", "API_SERVER", default = ""))
    )
    buildConfigField(
      "String",
      "WHITELABEL_DEFAULT_USER_AGENT",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_DEFAULT_USER_AGENT", "DEFAULT_USER_AGENT", default = "NovaStream/1.0 (Android)"))
    )
    buildConfigField(
      "String",
      "WHITELABEL_DEFAULT_ENGINE",
      asBuildConfigString(resolveWhitelabelEnv("WHITELABEL_DEFAULT_ENGINE", "DEFAULT_ENGINE", default = "EXOPLAYER"))
    )
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      isUniversalApk = true
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    jniLibs {
      useLegacyPackaging = true
      pickFirsts += "**/libc++_shared.so"
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val abiVersionMultipliers = mapOf(
  "armeabi-v7a" to 1,
  "x86" to 2,
  "arm64-v8a" to 3,
  "x86_64" to 4
)

abstract class SanitizeMergedManifestTask : DefaultTask() {
  @get:InputFile
  abstract val mergedManifest: RegularFileProperty

  @get:OutputFile
  abstract val updatedManifest: RegularFileProperty

  @TaskAction
  fun taskAction() {
    val xml = mergedManifest.get().asFile.readText()
    val sanitized = xml.replace(Regex("<application\\b[^>]*>")) { match ->
      match.value
        .replace(Regex("\\s+package=\"[^\"]*\""), "")
        .replace(Regex("\\s+android:versionCode=\"[^\"]*\""), "")
        .replace(Regex("\\s+android:versionName=\"[^\"]*\""), "")
    }
    updatedManifest.get().asFile.writeText(sanitized)
  }
}

androidComponents {
  onVariants { variant ->
    val sanitizeManifestTask = tasks.register<SanitizeMergedManifestTask>(
      "sanitize${variant.name.replaceFirstChar { it.uppercase() }}Manifest"
    )
    variant.artifacts.use(sanitizeManifestTask)
      .wiredWithFiles(
        SanitizeMergedManifestTask::mergedManifest,
        SanitizeMergedManifestTask::updatedManifest
      )
      .toTransform(SingleArtifact.MERGED_MANIFEST)

    val baseVersionCode = android.defaultConfig.versionCode ?: 1
    variant.outputs.forEach { output ->
      val abiName = output.filters.find { it.filterType == FilterType.ABI }?.identifier
      val multiplier = abiName?.let { abiVersionMultipliers[it] } ?: 0
      if (multiplier > 0) {
        output.versionCode.set(multiplier * 10000 + baseVersionCode)
      }
    }
  }
}

val debugApkOutputDir = layout.buildDirectory.dir("outputs/apk/debug")
val releaseApkOutputDir = layout.buildDirectory.dir("outputs/apk/release")

val allowedEmulatorAbis = setOf("x86_64", "arm64-v8a", "x86", "armeabi-v7a", "universal")
val rawEmulatorDebugAbi = resolveWhitelabelEnv("EMULATOR_DEBUG_ABI", "WHITELABEL_EMULATOR_DEBUG_ABI", default = "x86_64")
val resolvedEmulatorDebugAbi = if (rawEmulatorDebugAbi in allowedEmulatorAbis) rawEmulatorDebugAbi else "x86_64"

tasks.matching { it.name == "packageDebug" }.configureEach {
  val outDirProvider = debugApkOutputDir
  val targetAbi = resolvedEmulatorDebugAbi
  doLast {
    val dir = outDirProvider.get().asFile
    val preferredApk = File(dir, "app-$targetAbi-debug.apk")
    val fallbackApk = File(dir, "app-universal-debug.apk")
    val sourceApk = if (preferredApk.exists()) preferredApk else fallbackApk
    if (sourceApk.exists()) {
      sourceApk.copyTo(File(dir, "app-debug.apk"), overwrite = true)
    }
  }
}

tasks.matching { it.name == "packageRelease" }.configureEach {
  val outDirProvider = releaseApkOutputDir
  doLast {
    val dir = outDirProvider.get().asFile
    val universalApk = File(dir, "app-universal-release.apk")
    if (universalApk.exists()) {
      universalApk.copyTo(File(dir, "app-release.apk"), overwrite = true)
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("WHITELABEL_.*")
  ignoreList.add("APP_NAME")
  ignoreList.add("LAUNCHER_LABEL")
  ignoreList.add("APPLICATION_ID")
  ignoreList.add("APP_TAGLINE")
  ignoreList.add("BRAND_LOGO_URL")
  ignoreList.add("PRIMARY_COLOR")
  ignoreList.add("SECONDARY_COLOR")
  ignoreList.add("ACCENT_COLOR")
  ignoreList.add("BG_COLOR")
  ignoreList.add("SUPPORT_TEXT")
  ignoreList.add("SUPPORT_URL")
  ignoreList.add("API_SERVER")
  ignoreList.add("DEFAULT_USER_AGENT")
  ignoreList.add("DEFAULT_ENGINE")
  ignoreList.add("EMULATOR_DEBUG_ABI")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.rtsp)
  implementation(libs.androidx.media3.exoplayer.smoothstreaming)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.libvlc.all)
  implementation(libs.mpv.android.lib)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
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
