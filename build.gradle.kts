plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                // Compose seguem a convenção PascalCase para funções que emitem UI.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.md", "**/*.yml", "**/*.yaml", "**/*.json", "**/*.toml", "**/*.xml")
        targetExclude("**/build/**", "**/.gradle/**", "**/schemas/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
