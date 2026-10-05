import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.example.perfectoutfit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.perfectoutfit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.example.perfectoutfit.HiltTestRunner"

        // Web OAuth client from the Google Cloud Console; empty when not configured locally.
        val driveClientId = localProperties.getProperty("drive.oauth.client.id", "")
        buildConfigField("String", "DRIVE_OAUTH_CLIENT_ID", "\"$driveClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// AGP 9.x removed the legacy Java 'testClasses' task; stub it for IDE compatibility
tasks.register("testClasses")

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Background work
    implementation(libs.work.runtime.ktx)

    // Google sign-in and Drive authorization
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // DataStore
    implementation(libs.datastore.preferences)

    // Location
    implementation(libs.play.services.location)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// README screenshots from the demo outfits and forecast (androidTest ReadmeScreenshots), on a
// running emulator: ./gradlew readmeScreenshots.
abstract class ReadmeScreenshotsTask : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations

    @get:Internal abstract val adbExecutable: Property<File>
    @get:Internal abstract val appApk: RegularFileProperty
    @get:Internal abstract val testApk: RegularFileProperty
    @get:Internal abstract val applicationId: Property<String>
    @get:Internal abstract val testClass: Property<String>
    @get:Internal abstract val testRunner: Property<String>
    @get:Internal abstract val pullDir: DirectoryProperty
    @get:Internal abstract val screenshotsDir: DirectoryProperty

    @TaskAction
    fun capture() {
        val serial = emulatorSerial()
        val app = applicationId.get()
        logger.lifecycle("Taking README screenshots on $serial")
        adb(serial, "install", "-r", "-t", appApk.get().asFile.absolutePath)
        adb(serial, "install", "-r", "-t", testApk.get().asFile.absolutePath)
        // Emulator data is disposable; this also drops any Drive link so no backup worker fires mid-run.
        adb(serial, "shell", "pm", "clear", app)
        // The screenshots are light; remember the emulator's own setting to put it back.
        val nightMode = adb(serial, "shell", "cmd", "uimode", "night").substringAfter("Night mode:").trim()
        val iconBlocklist = adb(serial, "shell", "settings", "get", "secure", "icon_blacklist").trim()

        adb(serial, "shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        val output: String
        try {
            // Pixel-class 1080x2400 @ 420 dpi, whatever the AVD's own profile.
            adb(serial, "shell", "wm", "size", "1080x2400")
            adb(serial, "shell", "wm", "density", "420")
            adb(serial, "shell", "cmd", "uimode", "night", "no")
            // Demo mode's network icons are unreliable on Android 17 (stacked wifi, "3G" bars that
            // change from run to run), so the wifi and mobile slots are hidden instead.
            adb(serial, "shell", "settings", "put", "secure", "icon_blacklist", "wifi,mobile,airplane,ethernet,vpn")
            // Start demo mode afresh, in case an earlier run left it on.
            demo(serial, "exit")
            // SystemUI tears demo mode down asynchronously; let it finish before entering again.
            Thread.sleep(1_000)
            demo(serial, "enter")
            demo(serial, "clock", "-e", "hhmm", "1200")
            demo(serial, "battery", "-e", "level", "100", "-e", "plugged", "false")
            demo(
                serial, "status", "-e", "volume", "hide", "-e", "bluetooth", "hide", "-e", "location", "hide",
                "-e", "alarm", "hide", "-e", "sync", "hide", "-e", "zen", "hide", "-e", "mute", "hide",
                "-e", "speakerphone", "hide", "-e", "eri", "hide", "-e", "tty", "hide"
            )
            demo(serial, "notifications", "-e", "visible", "false")
            // Demo mode leaves some system notifications (e.g. Safety Center's "no screen lock") visible.
            adb(serial, "shell", "cmd", "statusbar", "send-disable-flag", "notification-icons")
            output = adb(
                serial, "shell", "am", "instrument", "-w",
                "-e", "readmeScreenshots", "true", "-e", "class", testClass.get(),
                "$app.test/${testRunner.get()}"
            )
        } finally {
            adb(serial, "shell", "cmd", "statusbar", "send-disable-flag", "none")
            demo(serial, "exit")
            if (iconBlocklist == "null") {
                adb(serial, "shell", "settings", "delete", "secure", "icon_blacklist")
            } else {
                adb(serial, "shell", "settings", "put", "secure", "icon_blacklist", iconBlocklist)
            }
            if (nightMode in setOf("yes", "no", "auto")) adb(serial, "shell", "cmd", "uimode", "night", nightMode)
            adb(serial, "shell", "wm", "size", "reset")
            adb(serial, "shell", "wm", "density", "reset")
        }
        logger.lifecycle(output.trim())
        // am instrument exits 0 even when a test fails.
        if (output.contains("FAILURES!!!") || !output.contains("OK (")) {
            throw GradleException("The README screenshot test failed; see the output above.")
        }

        val pulled = pullDir.get().asFile
        pulled.deleteRecursively()
        pulled.mkdirs()
        adb(serial, "pull", "/sdcard/Android/data/$app/files/readme-screenshots", pulled.absolutePath)
        val pngs = File(pulled, "readme-screenshots").listFiles { f -> f.name.endsWith(".png") }.orEmpty()
        if (pngs.isEmpty()) throw GradleException("No screenshots were pulled from $serial.")

        val target = screenshotsDir.get().asFile
        target.mkdirs()
        target.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        pngs.forEach { it.copyTo(File(target, it.name), overwrite = true) }
        logger.lifecycle("Saved ${pngs.map { it.name }.sorted().joinToString()} to $target")
    }

    /** The one running emulator (or ANDROID_SERIAL, which must be an emulator); real devices are never used. */
    private fun emulatorSerial(): String {
        System.getenv("ANDROID_SERIAL")?.takeIf { it.isNotBlank() }?.let { requested ->
            if (adb(requested, "shell", "getprop", "ro.kernel.qemu").trim() != "1") {
                throw GradleException("ANDROID_SERIAL=$requested is not an emulator; readmeScreenshots never runs on a physical device.")
            }
            return requested
        }
        val emulators = adb(null, "devices").lines()
            .map { it.split("\t") }
            .filter { it.size == 2 && it[1].trim() == "device" && it[0].startsWith("emulator-") }
            .map { it[0] }
        if (emulators.isEmpty()) throw GradleException("readmeScreenshots needs a running emulator; none found.")
        if (emulators.size > 1) {
            throw GradleException("Several emulators are running ($emulators); pick one with ANDROID_SERIAL.")
        }
        return emulators.single()
    }

    private fun demo(serial: String, command: String, vararg extras: String) {
        adb(serial, "shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", command, *extras)
    }

    private fun adb(serial: String?, vararg args: String): String {
        val commandLine = buildList {
            add(adbExecutable.get().absolutePath)
            if (serial != null) addAll(listOf("-s", serial))
            addAll(args)
        }
        val out = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(commandLine)
            standardOutput = out
        }
        return out.toString(Charsets.UTF_8)
    }
}

tasks.register<ReadmeScreenshotsTask>("readmeScreenshots") {
    group = "documentation"
    description = "Takes the README screenshots from the demo outfits on a running emulator."
    dependsOn("assembleDebug", "assembleDebugAndroidTest")
    adbExecutable.set(androidComponents.sdkComponents.adb.map { it.asFile })
    appApk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    testApk.set(layout.buildDirectory.file("outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
    applicationId.set(android.defaultConfig.applicationId)
    testClass.set("com.example.perfectoutfit.readme.ReadmeScreenshots")
    testRunner.set("com.example.perfectoutfit.HiltTestRunner")
    pullDir.set(layout.buildDirectory.dir("readme-screenshots"))
    screenshotsDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
    outputs.upToDateWhen { false }
}
