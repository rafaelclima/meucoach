plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.meucoach.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meucoach.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            // MapLibre adds ~13 MB per ABI; 32-bit phones are out of scope for the MVP.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// The golden gate is explicit: `:app:verifyRoborazziDebug`. Running the logic
// suite must not depend on Robolectric rendering full screens under load; the
// visual gate stays a separate invocation. Roborazzi tasks still configure
// their own verify/record mode.
val roborazziTaskRequested =
    gradle.startParameter.taskNames.any {
        it.contains("Roborazzi", ignoreCase = true)
    }
tasks.withType<Test>().configureEach {
    if (!roborazziTaskRequested) {
        systemProperty("roborazzi.test.verify", "false")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.maplibre)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Gate: line coverage never regresses below the value measured when the gate was
// introduced (78.44% on 13/11/2026). Raise it as coverage improves; never lower it.
kover {
    reports {
        verify {
            rule {
                minBound(78)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}

// Detekt (T-02): análise estática de build, sem dependência de runtime no APK.
// Dívida pré-existente vive no baseline; código novo não adiciona achados.
detekt {
    // O gate cobre só código de produção; testes seguem o processo de review usual.
    source.setFrom("src/main/kotlin", "src/main/java")
    buildUponDefaultConfig = true
    parallel = true
    config.from(rootProject.files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
}

tasks.named("check") {
    dependsOn(tasks.matching { it.name == "detekt" || it.name == "detektDebug" })
}
