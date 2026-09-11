import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

base {
    val versionCode = project.property("VERSION_CODE").toString().toInt()
    archivesName = "Texto-sms-$versionCode"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        // The installed package id. Kept separate from `namespace` below, which still
        // points at the original package so R and BuildConfig keep resolving without
        // moving every source file.
        applicationId = project.property("APPLICATION_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        // The two languages the app ships. Everything else Fossify Commons carries is
        // stripped, so a phone set to German gets the English UI rather than a half:
        // translated one built out of commons' own strings.
        resConfigs("en", "fa")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        // A checked-in debug key so every build (local or CI) shares one signature and
        // updates install over each other. Never used for a real release.
        val sharedDebugKeystore = rootProject.file("debug.keystore")
        if (sharedDebugKeystore.exists()) {
            getByName("debug") {
                storeFile = sharedDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // No suffix: debug builds are the ones actually installed and shipped here.
            // Because this is a shipped build and not a development one, it must not stay
            // debuggable -- that flag lets anyone with adb read the message database straight
            // out of the app's private storage. Nothing in the app reads BuildConfig.DEBUG.
            isDebuggable = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

// org.fossify:commons pulls in com.github.aritraroy:patternLockView, a 2017 library that
// still asks for com.android.support:appcompat-v7:25.3.0. androidx.core ships the very same
// android.support.v4.* compatibility classes (INotificationSideChannel, ResultReceiver and
// friends), so having both on the classpath fails checkDuplicateClasses. Jetifier -- on, in
// gradle.properties -- rewrites patternLockView's own references to their androidx
// equivalents, which leaves the legacy artifacts as pure duplication rather than something
// anything still loads.
configurations.configureEach {
    exclude(group = "com.android.support")

    // Commons drags the whole Compose stack in, and this app has no Compose in it: measured
    // on the debug APK, 12,778 of the main dex's 16,799 classes were androidx.compose, and
    // 10,372 of those were material-icons-extended on its own. Commons uses Compose for
    // screens this app does not open -- its About and FAQ, which the settings sheet stopped
    // calling when it grew its own -- so none of it is reachable here.
    //
    // Excluded rather than shrunk away because R8 only runs on release, so every debug build
    // and every install was paying for it. This is a stopgap: the dependency itself is on its
    // way out, and these lines go with it.
    exclude(group = "androidx.compose.material")
    exclude(group = "androidx.compose.foundation")
    exclude(group = "androidx.compose.animation")
    exclude(group = "androidx.compose.ui")
    exclude(group = "androidx.compose.runtime")
    exclude(group = "androidx.activity", module = "activity-compose")
    exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-compose")
    exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-compose")

    // The app-lock feature's biometric half. Commons offers a pattern/PIN/fingerprint lock;
    // this app draws its own settings screen and offers no lock, and FossifyApp's
    // isAppLockFeatureAvailable is read by nothing in commons 6.1.5 -- checked against the
    // library's own bytecode. The manifest already removes the two permissions this brought.
    exclude(group = "com.github.tibbi", module = "reprint")
    exclude(group = "androidx.biometric")

    // Commons' own view pager, for screens this app does not open.
    exclude(group = "com.github.naveensingh", module = "rtl-viewpager")

    // patternLockView and RecyclerView-FastScroller are *not* excluded, though nothing here
    // uses either: commons' own resources reference theirs (dimen/corner_radius,
    // color/colorPrimary), so dropping the artifacts fails resource linking rather than
    // merely shrinking the build. They come out with commons itself.
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.eventbus)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.mmslib)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp("com.github.bumptech.glide:ksp:5.0.7")
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)

    testImplementation("junit:junit:4.13.2")
}
