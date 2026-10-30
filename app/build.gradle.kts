import groovy.json.JsonOutput
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load)
}
fun devProp(key: String) = "\"" + (localProps.getProperty(key) ?: "") + "\""

// Release signing key lives outside the repo (see README "Release"). Lose it and the car
// can't take updates without an uninstall, so back it up.
val releaseStore = localProps.getProperty("motordash.release.storeFile")?.let(::file)?.takeIf { it.exists() }

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    namespace = "app.motordash"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The installed app's identity, set in gradle.properties. Never change it after a release:
        // Android would treat it as a different app (no updates, separate data).
        applicationId = providers.gradleProperty("motordash.applicationId").get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Must go up by one for every APK handed out; the updater compares this.
        versionCode = 2
        // Betas lead up to 0.1.0: 0.1.0-beta.1, -beta.2, … then 0.1.0. Shown as "Beta" in Settings.
        versionName = "0.1.0-beta.1"
    }
    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = localProps.getProperty("motordash.release.storePassword")
            keyAlias = localProps.getProperty("motordash.release.keyAlias")
            keyPassword = localProps.getProperty("motordash.release.keyPassword") ?: storePassword
        }
    }
    buildTypes {
        getByName("debug") {
            // A separate app next to the release one: own package, name and folder.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "MotorDash Dev"
            buildConfigField("String", "FILES_FOLDER", "\"MotorDash Dev\"")
            buildConfigField("String", "UPDATE_URL", "\"\"") // the dev app updates by hot reload
            // Defaults baked into the dev APK; both can be changed on the car via the dev badge.
            buildConfigField("String", "DEV_SERVER_URL", devProp("motordash.devServerUrl"))
            buildConfigField("String", "DEV_TOKEN", devProp("motordash.devToken"))
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["appLabel"] = "MotorDash"
            buildConfigField("String", "FILES_FOLDER", "\"MotorDash\"")
            // Where the app looks for update.json; empty disables in-app updates.
            buildConfigField("String", "UPDATE_URL", devProp("motordash.updateUrl"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":dashboard-api"))
    implementation(project(":dashboard"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.nanohttpd)

    testImplementation(libs.junit)
    // Real org.json; the android.jar copy is a stub in unit tests.
    testImplementation(libs.org.json)
    // App start-up smoke test on the JVM: there's no ADB on the car to see crashes.
    testImplementation(libs.robolectric)
}

// Never ship an unsigned or debug-signed release by accident.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (releaseStore == null) {
            throw GradleException("No release key: set motordash.release.storeFile/storePassword/keyAlias in local.properties")
        }
    }
}

// ---- Release files: the APK plus update.json for in-app updates (see README "Updates") ----
tasks.register("releaseFiles") {
    description = "Builds the release APK and writes build/release/{MotorDash-<version>.apk,update.json}."
    dependsOn("assembleRelease")
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val outDir = layout.buildDirectory.dir("release")
    val versionCode = android.defaultConfig.versionCode!!
    val versionName = android.defaultConfig.versionName!!
    val notes = providers.gradleProperty("releaseNotes").orElse("")
    // Declared inputs, or Gradle calls the task up to date and keeps an older APK here.
    inputs.file(apkDir.map { it.file("app-release.apk") })
    inputs.property("versionCode", versionCode)
    inputs.property("versionName", versionName)
    inputs.property("notes", notes)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val apkName = "MotorDash-$versionName.apk"
        val apk = apkDir.get().asFile.resolve("app-release.apk").copyTo(out.resolve(apkName))
        val sha256 = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val json = JsonOutput.toJson(
            mapOf("versionCode" to versionCode, "versionName" to versionName, "apk" to apkName, "sha256" to sha256, "notes" to notes.get())
        )
        out.resolve("update.json").writeText(JsonOutput.prettyPrint(json) + "\n")
        println("Release files in $out: upload both, so update.json is served at motordash.updateUrl")
    }
}

