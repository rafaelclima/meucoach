plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
}

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
