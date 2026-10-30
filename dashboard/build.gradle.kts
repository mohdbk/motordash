import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.motordash.dashboard"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    testOptions.unitTests.isIncludeAndroidResources = true
    // Screenshot tests render with the real Dubai font from the app's assets.
    sourceSets.getByName("test").assets.srcDir("../app/src/main/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":dashboard-api"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.zxing.core)

    // Screenshot tests: render the real screens on the JVM (./gradlew :dashboard:testDebugUnitTest).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.activity.compose)
}

// ---- Hot reload payload: this module's classes only, dexed for the shell to load at runtime ----

val hotDir = layout.buildDirectory.dir("hot")

val hotPayloadClasses = tasks.register<Jar>("hotPayloadClasses") {
    dependsOn("compileDebugKotlin")
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    exclude("META-INF/**")
    archiveFileName.set("dashboard-classes.jar")
    destinationDirectory.set(hotDir)
    // A build running at the same time can leave the classes folder empty for a moment. Fail
    // instead of handing the car an empty payload; the last good one stays in place.
    doLast {
        val jar = archiveFile.get().asFile
        val hasEntry = ZipFile(jar).use { zip -> zip.entries().asSequence().any { it.name.endsWith("DashboardUiImpl.class") } }
        if (!hasEntry) throw GradleException("Dashboard classes missing from ${jar.name}; rerun the build")
    }
}

// D8 as bundled with AGP, so it always understands the Kotlin version in use.
val d8Tool = configurations.create("d8Tool") { isTransitive = false }
dependencies { d8Tool("com.android.tools.build:builder:${libs.versions.agp.get()}") }

val androidJar = android.sdkDirectory.resolve("platforms/android-${libs.versions.compileSdk.get()}/android.jar")
val minSdk = libs.versions.minSdk.get()

tasks.register<JavaExec>("hotPayload") {
    description = "Builds build/hot/dashboard-payload.jar for the dev server to push to the car."
    val input = hotPayloadClasses.flatMap { it.archiveFile }
    val output = hotDir.map { it.file("dashboard-payload.jar") }
    inputs.file(input)
    outputs.file(output)
    classpath = d8Tool
    mainClass.set("com.android.tools.r8.D8")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--debug", "--min-api", minSdk, "--lib", androidJar.path,
            "--output", output.get().asFile.path, input.get().asFile.path,
        )
    })
}

// Roborazzi only writes images in record mode.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
}
