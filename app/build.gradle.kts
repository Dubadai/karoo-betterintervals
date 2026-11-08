import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// GitHub release assets: the APK is always betterintervals.apk, so these links always point at
// the newest (non-pre-)release.
val RELEASES = "https://github.com/Dubadai/karoo-betterintervals/releases/latest/download"

// Release signing key, kept out of git. See README "Publishing a release".
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.daviddokakis.karoointerval"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daviddokakis.karoointerval"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "0.7.1"
        // Where the Karoo and UpdateCheck find the latest version. To test the update notice,
        // build with -PmanifestUrl=http://localhost:8791/manifest.json (see README "Update notices").
        manifestPlaceholders["manifestUrl"] =
            (project.findProperty("manifestUrl") as String?) ?: "$RELEASES/manifest.json"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Your own release key when keystore.properties exists; otherwise the debug key, so
            // the project still builds anywhere (but such APKs can't update a published one).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":karoo-ext"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}

// manifest.json for the GitHub release (upload it next to betterintervals.apk). The Karoo's
// Settings › Apps page and UpdateCheck read it to offer the update.
//   ./gradlew -q :app:releaseManifest -PreleaseNotes="What's new…"
tasks.register("releaseManifest") {
    group = "release"
    description = "Writes build.nosync/manifest.json for the GitHub release"
    val out = layout.buildDirectory.file("manifest.json")
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val notes = (project.findProperty("releaseNotes") as String?).orEmpty()
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val manifest = mapOf(
            "label" to "DDB BetterIntervals",
            "packageName" to "com.daviddokakis.karoointerval",
            "latestApkUrl" to "$RELEASES/betterintervals.apk",
            "latestVersion" to name,
            "latestVersionCode" to code,
            "developer" to "github.com/Dubadai",
            "description" to "Your intervals.icu workout on the Karoo: target watts, interval timer and the workout chart on one screen.",
            "releaseNotes" to notes,
            "tags" to listOf("performance"),
        )
        val f = out.get().asFile
        f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifest)) + "\n")
        println("Wrote $f ($name, $code)")
    }
}
