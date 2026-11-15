import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration.FilterType
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
val resolvedVersionName = resolveWhitelabelEnv(
  "WHITELABEL_VERSION_NAME",
  "APP_VERSION_NAME",
  "VERSION_NAME",
  default = "1.0"
)
val resolvedVersionCode = resolveWhitelabelEnv(
  "WHITELABEL_VERSION_CODE",
  "APP_VERSION_CODE",
  "VERSION_CODE",
  default = "1"
).toIntOrNull()?.takeIf { it > 0 } ?: 1

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = resolvedApplicationId
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

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
val workspaceArtifactsRootDir = rootProject.layout.projectDirectory.dir("artifacts")

val allowedEmulatorAbis = setOf("x86_64", "arm64-v8a", "x86", "armeabi-v7a", "universal")
val rawEmulatorDebugAbi = resolveWhitelabelEnv("EMULATOR_DEBUG_ABI", "WHITELABEL_EMULATOR_DEBUG_ABI", default = "x86_64")
val resolvedEmulatorDebugAbi = if (rawEmulatorDebugAbi in allowedEmulatorAbis) rawEmulatorDebugAbi else "x86_64"
val resolvedBrandNameForArtifacts = resolveWhitelabelEnv("WHITELABEL_APP_NAME", "APP_NAME", default = "NovaStream Player")

abstract class SyncApkArtifactsTask : DefaultTask() {
  @get:Internal
  abstract val sourceApkDir: DirectoryProperty

  @get:Input
  abstract val variantName: Property<String>

  @get:Input
  abstract val applicationId: Property<String>

  @get:Input
  abstract val appDisplayName: Property<String>

  @get:Input
  abstract val emulatorAbi: Property<String>

  @get:OutputDirectory
  abstract val destinationDir: DirectoryProperty

  init {
    outputs.upToDateWhen { false }
  }

  private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  @TaskAction
  fun exportArtifacts() {
    val srcDir = sourceApkDir.get().asFile
    if (!srcDir.exists()) return

    val destDir = destinationDir.get().asFile
    destDir.mkdirs()

    val variant = variantName.get()
    val aliasFileName = "app-$variant.apk"
    val apkFiles = srcDir.listFiles()
      ?.filter { it.isFile && it.extension == "apk" && it.name != aliasFileName }
      ?.sortedBy { it.name }
      .orEmpty()

    if (apkFiles.isEmpty()) return

    val expectedNames = apkFiles.map { it.name }.toSet() +
      setOf("output-metadata.json", "SHA256SUMS.txt", "BUILD_MANIFEST.md")
    destDir.listFiles()?.forEach { existing ->
      if (existing.isFile && existing.name !in expectedNames) {
        existing.delete()
      }
    }

    val checksumLines = mutableListOf<String>()
    val tableRows = mutableListOf<String>()

    for (apk in apkFiles) {
      val targetFile = File(destDir, apk.name)
      apk.copyTo(targetFile, overwrite = true)
      val sha256 = sha256Hex(targetFile)
      val sizeMib = String.format(Locale.US, "%.2f MiB", targetFile.length().toDouble() / (1024.0 * 1024.0))
      val abiLabel = apk.name
        .removePrefix("app-")
        .removeSuffix("-$variant.apk")
      checksumLines += "$sha256  ${targetFile.name}"
      tableRows += "| `${targetFile.name}` | `$abiLabel` | $sizeMib | `${sha256.take(16)}…` |"
    }

    val metadataFile = File(srcDir, "output-metadata.json")
    if (metadataFile.exists()) {
      metadataFile.copyTo(File(destDir, "output-metadata.json"), overwrite = true)
    }

    File(destDir, "SHA256SUMS.txt").writeText(checksumLines.joinToString("\n") + "\n")

    val manifestMd = buildString {
      appendLine("# ${appDisplayName.get()} — ${variant.replaceFirstChar { it.uppercase() }} APK Artifacts")
      appendLine()
      appendLine("- **Application ID:** `${applicationId.get()}`")
      appendLine("- **Build Variant:** `$variant`")
      if (variant == "debug") {
        appendLine("- **Emulator Synced ABI (`.build-outputs/app-debug.apk`):** `${emulatorAbi.get()}`")
      }
      appendLine()
      appendLine("| APK File | Target ABI | Size | SHA-256 (prefix) |")
      appendLine("| :--- | :--- | ---: | :--- |")
      tableRows.forEach { appendLine(it) }
    }
    File(destDir, "BUILD_MANIFEST.md").writeText(manifestMd)
  }
}

val syncDebugBuildsToArtifacts = tasks.register<SyncApkArtifactsTask>("syncDebugBuildsToArtifacts") {
  sourceApkDir.set(debugApkOutputDir)
  variantName.set("debug")
  applicationId.set(resolvedApplicationId)
  appDisplayName.set(resolvedBrandNameForArtifacts)
  emulatorAbi.set(resolvedEmulatorDebugAbi)
  destinationDir.set(workspaceArtifactsRootDir.dir("debug"))
}

val syncReleaseBuildsToArtifacts = tasks.register<SyncApkArtifactsTask>("syncReleaseBuildsToArtifacts") {
  sourceApkDir.set(releaseApkOutputDir)
  variantName.set("release")
  applicationId.set(resolvedApplicationId)
  appDisplayName.set(resolvedBrandNameForArtifacts)
  emulatorAbi.set("universal")
  destinationDir.set(workspaceArtifactsRootDir.dir("release"))
}

val archiveBrandSlug = resolvedBrandNameForArtifacts
  .lowercase(Locale.US)
  .replace(Regex("[^a-z0-9]+"), "-")
  .trim('-')
  .ifBlank { "novastream" }
val archiveSafeVersionName = resolvedVersionName
  .replace(Regex("[^a-zA-Z0-9._+-]+"), "-")
  .trim('-')
  .ifBlank { "1.0" }
val versionedArtifactsZipName = "$archiveBrandSlug-v$archiveSafeVersionName-vc$resolvedVersionCode-artifacts.zip"

abstract class PackageArtifactsArchiveTask : DefaultTask() {
  @get:Internal
  abstract val artifactsRootDir: DirectoryProperty

  @get:Input
  abstract val archiveFileName: Property<String>

  @get:Input
  abstract val appDisplayName: Property<String>

  @get:Input
  abstract val applicationId: Property<String>

  @get:Input
  abstract val versionName: Property<String>

  @get:Input
  abstract val versionCode: Property<Int>

  @get:OutputFile
  abstract val archiveOutputFile: RegularFileProperty

  @get:OutputFile
  abstract val checksumOutputFile: RegularFileProperty

  @get:OutputFile
  abstract val manifestOutputFile: RegularFileProperty

  init {
    outputs.upToDateWhen { false }
  }

  private fun computeCrc32(file: File): Long {
    val crc = CRC32()
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        crc.update(buffer, 0, read)
      }
    }
    return crc.value
  }

  private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  @TaskAction
  fun createVersionedArchive() {
    val rootDir = artifactsRootDir.get().asFile
    if (!rootDir.exists()) return

    val outZip = archiveOutputFile.get().asFile
    val outSha = checksumOutputFile.get().asFile
    val outManifest = manifestOutputFile.get().asFile
    outZip.parentFile?.mkdirs()

    val targetZipName = archiveFileName.get()
    val excludedRootNames = setOf(
      targetZipName,
      outSha.name,
      outManifest.name
    )

    // Remove stale versioned archives from earlier version bumps so artifacts/ stays tidy
    rootDir.listFiles()?.forEach { f ->
      if (f.isFile &&
        f.name !in excludedRootNames &&
        (f.name.endsWith("-artifacts.zip") || f.name.endsWith("-artifacts.zip.sha256") || f.name.startsWith(".tmp-"))
      ) {
        f.delete()
      }
    }

    val filesToArchive = rootDir.walkTopDown()
      .filter { file ->
        if (!file.isFile) return@filter false
        val rel = file.relativeTo(rootDir).invariantSeparatorsPath
        if (rel in excludedRootNames) return@filter false
        if (file.name.endsWith(".zip") || file.name.endsWith(".zip.sha256") || file.name.startsWith(".tmp-")) {
          return@filter false
        }
        true
      }
      .sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
      .toList()

    if (filesToArchive.isEmpty()) return

    val tempZip = File(rootDir, ".tmp-$targetZipName")
    val entryRows = mutableListOf<String>()

    ZipOutputStream(tempZip.outputStream().buffered(64 * 1024)).use { zos ->
      val copyBuffer = ByteArray(64 * 1024)
      for (file in filesToArchive) {
        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
        val entry = ZipEntry(relPath).apply {
          time = file.lastModified()
          if (file.extension.equals("apk", ignoreCase = true)) {
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = file.length()
            crc = computeCrc32(file)
          } else {
            method = ZipEntry.DEFLATED
          }
        }
        zos.putNextEntry(entry)
        file.inputStream().buffered(64 * 1024).use { input ->
          while (true) {
            val read = input.read(copyBuffer)
            if (read <= 0) break
            zos.write(copyBuffer, 0, read)
          }
        }
        zos.closeEntry()

        val sizeStr = if (file.length() >= 1024 * 1024) {
          String.format(Locale.US, "%.2f MiB", file.length().toDouble() / (1024.0 * 1024.0))
        } else {
          String.format(Locale.US, "%.2f KiB", file.length().toDouble() / 1024.0)
        }
        val modeLabel = if (file.extension.equals("apk", ignoreCase = true)) "STORED" else "DEFLATED"
        entryRows += "| `$relPath` | $sizeStr | `$modeLabel` |"
      }
    }

    if (outZip.exists()) outZip.delete()
    tempZip.renameTo(outZip)

    val zipSha256 = computeSha256(outZip)
    val zipSizeMib = String.format(Locale.US, "%.2f MiB", outZip.length().toDouble() / (1024.0 * 1024.0))
    outSha.writeText("$zipSha256  ${outZip.name}\n")

    val archiveSummary = buildString {
      appendLine("# ${appDisplayName.get()} — Versioned Distribution Archive")
      appendLine()
      appendLine("- **Archive File:** `${outZip.name}`")
      appendLine("- **Version:** `${versionName.get()}` (`versionCode = ${versionCode.get()}`)")
      appendLine("- **Application ID:** `${applicationId.get()}`")
      appendLine("- **Archive Size:** $zipSizeMib (${outZip.length()} bytes)")
      appendLine("- **SHA-256:** `$zipSha256`")
      appendLine()
      appendLine("## Packaged Entries (${filesToArchive.size})")
      appendLine()
      appendLine("| Relative Path | Size | ZIP Method |")
      appendLine("| :--- | ---: | :--- |")
      entryRows.forEach { appendLine(it) }
    }
    outManifest.writeText(archiveSummary)
  }
}

val zipArtifacts = tasks.register<PackageArtifactsArchiveTask>("zipArtifacts") {
  description = "Packages the contents of the workspace artifacts folder into a versioned distribution ZIP archive."
  group = "distribution"
  artifactsRootDir.set(workspaceArtifactsRootDir)
  archiveFileName.set(versionedArtifactsZipName)
  appDisplayName.set(resolvedBrandNameForArtifacts)
  applicationId.set(resolvedApplicationId)
  versionName.set(resolvedVersionName)
  versionCode.set(resolvedVersionCode)
  archiveOutputFile.set(workspaceArtifactsRootDir.file(versionedArtifactsZipName))
  checksumOutputFile.set(workspaceArtifactsRootDir.file("$versionedArtifactsZipName.sha256"))
  manifestOutputFile.set(workspaceArtifactsRootDir.file("ARCHIVE_MANIFEST.md"))
  mustRunAfter(syncDebugBuildsToArtifacts, syncReleaseBuildsToArtifacts)
}

syncDebugBuildsToArtifacts.configure {
  finalizedBy(zipArtifacts)
}

syncReleaseBuildsToArtifacts.configure {
  finalizedBy(zipArtifacts)
}

tasks.matching { it.name == "packageDebug" }.configureEach {
  val outDirProvider = debugApkOutputDir
  val targetAbi = resolvedEmulatorDebugAbi
  inputs.property("emulatorDebugAbi", targetAbi)
  finalizedBy(syncDebugBuildsToArtifacts)
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

tasks.matching { it.name == "assembleDebug" }.configureEach {
  finalizedBy(syncDebugBuildsToArtifacts, zipArtifacts)
}

tasks.matching { it.name == "packageRelease" }.configureEach {
  val outDirProvider = releaseApkOutputDir
  finalizedBy(syncReleaseBuildsToArtifacts)
  doLast {
    val dir = outDirProvider.get().asFile
    val universalApk = File(dir, "app-universal-release.apk")
    if (universalApk.exists()) {
      universalApk.copyTo(File(dir, "app-release.apk"), overwrite = true)
    }
  }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
  finalizedBy(syncReleaseBuildsToArtifacts, zipArtifacts)
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
  ignoreList.add("APP_VERSION_NAME")
  ignoreList.add("APP_VERSION_CODE")
  ignoreList.add("VERSION_NAME")
  ignoreList.add("VERSION_CODE")
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
