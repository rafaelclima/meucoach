plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.meucoach.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.vico.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
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
