plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.assertj)
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
