import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.time.Duration

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.ben.manes)
}

group = "com.ahogek"
version = libs.versions.appVersion.get()
description = "ctt-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Process resources to replace version placeholders
tasks.processResources {
    val appVersion = libs.versions.appVersion.get()
    filteringCharset = "UTF-8"
    inputs.property("appVersion", appVersion)
    filesMatching("**/application.yaml") {
        filter { line ->
            line.replace("@appVersion@", appVersion)
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-core") {
        // The BOM ships an older flyway-core than the database-postgresql module this project adds,
        // and that module's POM requires them at the same version. Reading the catalog keeps the two
        // from drifting apart, which a second literal in this file would allow.
        version { strictly(libs.versions.flyway.get()) }
    }
    implementation(libs.flyway.database.postgresql)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation(libs.otp.java)
    implementation(libs.commons.codec)
    implementation(libs.webauthn.server.core)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // Metrics exit: the Prometheus registry is version-managed by the Boot BOM, so no literal here.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    // Tracing: Boot autoconfigures the OpenTelemetry bridge and the OTLP exporter; the bridge and
    // the exporter are version-managed by the BOM, so no literals here.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly(libs.org.postgresql)
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.greenmail)
    testImplementation("org.awaitility:awaitility")
    testImplementation("io.micrometer:micrometer-tracing-test")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // A hung test must fail the build instead of holding the runner until its six-hour limit.
    timeout = Duration.ofMinutes(20)
    // Testcontainers startup is class-scoped, so a single test method should never need this long.
    systemProperty("junit.jupiter.execution.timeout.default", "5 m")
}

// Code formatting with Spotless
spotless {
    java {
        googleJavaFormat("1.36.1").aosp()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("com.ahogek", "jakarta", "org", "java", "javax", "", "static ")
    }
    kotlinGradle {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Exclude logback-spring.xml from formatting to preserve single-line patterns
    format("xml") {
        target("src/**/*.xml")
        targetExclude("src/**/logback-spring.xml")
    }
}

// Test coverage with JaCoCo
jacoco {
    toolVersion = "0.8.16"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// The threshold was defined but never attached, so registration alone enforced nothing.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Downloads every artifact the build needs without compiling anything.
//
// The Docker build runs this in its own layer before `src/` travels, so an
// unchanged dependency set is a layer cache hit and the image build never
// reaches a remote repository at all — the download is both the slowest part of
// a cold image build and the part a throttled registry breaks. The
// configurations are declared as inputs instead of being resolved in `doLast`,
// because a task that touches `configurations` while it runs cannot use the
// configuration cache — and the configurations are passed as themselves, never
// through an `artifactView {}` whose spec is a script closure the cache cannot
// serialize.
val downloadableDependencies = files(configurations.filter { it.isCanBeResolved })
val dependenciesResolved = layout.buildDirectory.file("downloadDependencies.ok")

tasks.register("downloadDependencies") {
    description = "Resolves and downloads every dependency without compiling."
    group = "build"
    inputs.files(downloadableDependencies)
    // Bound to a local: a `doLast` that reads a script property keeps the whole
    // script instance alive in the action, which the configuration cache refuses
    // to serialize ("cannot serialize Gradle script object references").
    val marker = dependenciesResolved.get().asFile
    outputs.file(marker)
    doLast { marker.apply { parentFile.mkdirs() }.writeText("resolved\n") }
}

fun String.isNonStable(): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(this)
    return isStable.not()
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    rejectVersionIf {
        candidate.version.isNonStable()
    }

    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}
