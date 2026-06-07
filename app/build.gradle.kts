import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.reygnn.nah"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.reygnn.nah"
        minSdk = 36
        targetSdk = 36
        versionCode = 37
        versionName = "0.7.27"
    }

    buildTypes {
        release {
            // R8 bewusst aus: keine Reflection im Code, und für eine persönliche Single-User-App
            // rechtfertigt der marginal kleinere AAB nicht das Compose-Keep-Regel-Tuning + den
            // Testzyklus. Nicht versehentlich „zur Optimierung" einschalten.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Kein signingConfig: das Release-AAB bleibt unsigniert. Signiert wird
            // mit dem geteilten Family-Key durch ~/apk/install-aab.sh beim
            // Installieren, nicht durch Gradle (siehe ~/.claude/CLAUDE.md).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true // für BuildConfig.VERSION_NAME (Versionsanzeige im Settings-Screen)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
