plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("fixtures"))
    }
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

abstract class VerifyParserBranchCoverageTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val reportFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val report = reportFile.get().asFile
        check(report.isFile) { "Missing Kover XML report at ${report.path}" }

        val document =
            javax.xml.parsers.DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(report)
        val classes = document.getElementsByTagName("class")
        var missed = 0
        var covered = 0
        for (index in 0 until classes.length) {
            val element = classes.item(index) as org.w3c.dom.Element
            if (!element.getAttribute("name").startsWith("com/meucoach/core/domain/parser/fit/")) {
                continue
            }
            val counters = element.childNodes
            for (counterIndex in 0 until counters.length) {
                val counter = counters.item(counterIndex)
                if (counter !is org.w3c.dom.Element || counter.tagName != "counter") continue
                if (counter.getAttribute("type") != "BRANCH") continue
                missed += counter.getAttribute("missed").toInt()
                covered += counter.getAttribute("covered").toInt()
            }
        }
        val total = missed + covered
        check(total > 0) { "No FIT parser branch data found in ${report.path}" }
        val percentage = covered * 100.0 / total
        println("FIT parser branch coverage: %.2f%% (%d/%d)".format(percentage, covered, total))
        if (percentage < 90.0) {
            throw org.gradle.api.GradleException(
                "FIT parser branch coverage is %.2f%%, expected at least 90%%".format(percentage),
            )
        }
    }
}

val verifyParserBranchCoverage =
    tasks.register<VerifyParserBranchCoverageTask>("verifyParserBranchCoverage") {
        group = "verification"
        description = "Fails when the FIT parser package has less than 90% branch coverage."
        dependsOn("koverXmlReport")
        reportFile.set(layout.buildDirectory.file("reports/kover/report.xml"))
    }

tasks.named("koverVerify") {
    dependsOn(verifyParserBranchCoverage)
}

tasks.named("check") {
    dependsOn(verifyParserBranchCoverage)
}
