import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

/**
 * Release signing comes from local.properties (gitignored):
 *   release.storeFile, release.storePassword, release.keyAlias, release.keyPassword
 * Without them a release build is signed with the debug key, which is fine for trying R8 output
 * on a phone but can't be uploaded to Play or upgraded by a properly signed build later.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun local(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.ganim.scanfire"
    // Compose 1.12 requires compileSdk 37; targetSdk stays at what Play requires (36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ganim.scanfire"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        val storeFile = local("release.storeFile")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = local("release.storePassword")
                keyAlias = local("release.keyAlias")
                keyPassword = local("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            // BouncyCastle (via PdfBox) ships ~4 MB of post-quantum parameter tables; PDF AES encryption never uses them.
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/x509/**"
        }
    }

    // ML Kit translation's native library is ~17 MB per ABI. Play delivers one ABI from the App Bundle
    // automatically; for APKs on GitHub, build with -PsplitAbi to get one APK per CPU type.
    if (providers.gradleProperty("splitAbi").isPresent) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86_64")
                isUniversalApk = false
            }
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
        unitTests.all { it.useJUnitPlatform() }
        // PdfBox-Android logs through android.util.Log; let those stubs no-op in JVM tests.
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * PdfBox-Android keeps CMaps, glyph lists and fonts as Android assets, which JVM unit tests can't see.
 * Unpack them from the library onto the test classpath (tests only; nothing extra ships).
 */
val pdfboxTestAssets = layout.buildDirectory.dir("pdfbox-test-assets")
val pdfboxAar = configurations.detachedConfiguration(dependencies.create("com.tom-roush:pdfbox-android:${libs.versions.pdfbox.get()}@aar"))
val extractPdfboxTestAssets = tasks.register<Copy>("extractPdfboxTestAssets") {
    from({ pdfboxAar.map { zipTree(it) } }) {
        include("assets/**")
        eachFile { path = path.removePrefix("assets/") }
        includeEmptyDirs = false
    }
    into(pdfboxTestAssets)
}
android.sourceSets.getByName("test").resources.srcDir(pdfboxTestAssets.get().asFile)
tasks.matching { it.name.endsWith("UnitTestJavaRes") }.configureEach { dependsOn(extractPdfboxTestAssets) }

// The exported Room schema is committed so every migration can be reviewed against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.reorderable)
    implementation(libs.androidx.biometric)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.tesseract.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.code.scanner)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
