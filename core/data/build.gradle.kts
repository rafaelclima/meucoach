plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.meucoach.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("test").assets.directories.add("$projectDir/schemas")
        getByName("test").resources.directories.add(rootProject.file("fixtures").absolutePath)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Gate: line coverage never regresses below the value measured when the gate was
// introduced (85.15% on 13/11/2026). Raise it as coverage improves; never lower it.
kover {
    reports {
        verify {
            rule {
                minBound(85)
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

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    // RoomDatabase is part of MeuCoachDatabase's public type, so consumers need it at compile time.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    api(libs.androidx.health.connect.client)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
}
