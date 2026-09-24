import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Cle de signature "upload" pour la publication Play Store (voir android/key.properties,
// jamais commite -- exclu par .gitignore). Absent en local/CI sans le fichier : les builds
// debug restent inchanges, seul `flutter build appbundle/apk --release` en a besoin.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.lecomptoir.converter"
    // flutter_secure_storage >= 11 exige compileSdk 37 (message explicite du build Gradle).
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.lecomptoir.converter"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // key.properties absent (poste sans la cle de publication) : repli sur la cle
            // debug pour que `flutter run --release` continue de fonctionner localement --
            // jamais le cas pour un build reellement soumis au Play Store.
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }
}

flutter {
    // Racine du projet Flutter, relative a android/app. La valeur "app/src"
    // faisait resoudre .../android/app/app/src -> "Invalid Flutter source directory".
    source = "../.."
}

// AGP 9.0 : l'ancien bloc `kotlinOptions { jvmTarget = ... }` est desormais une erreur de
// compilation du script (pas seulement un avertissement) -- DSL unifiee `compilerOptions`
// du plugin Kotlin a la place (https://kotl.in/u1r8ln).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
