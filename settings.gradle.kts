import org.gradle.api.initialization.resolve.RepositoriesMode

// Every dependency this build resolves comes from the mirror declared first.
// Maven Central throttles shared runner addresses with 429, so the default
// `mavenCentral()`-only source is a reliability defect rather than a default
// worth keeping. Gradle has no mirror feature of its own (gradle/gradle#27808);
// declaring the repositories here is the documented substitute, and
// `FAIL_ON_PROJECT_REPOS` keeps a project from adding a second source behind
// this one's back.
//
// The mirror is Google's unauthenticated copy of Maven Central — no account, no
// cost, byte-identical. It does not have to be trusted: every artifact is
// pinned by sha256 in gradle/verification-metadata.xml, so bytes that differ
// from Central fail the build instead of being accepted. Central stays listed
// after it for the one case the mirror does not cover — an artifact newer than
// the mirror's replication.
//
// Plugin and buildscript resolution uses the same mirror, first. The Plugin
// Portal is not a source of bytes for most plugins: it redirects to Maven
// Central (`plugins.gradle.org/m2/…` → `repo.maven.apache.org`), which is the
// very host that rate-limits. Leaving this block out is what killed a Publish
// run on 429 while the CI run, whose cache was warm, stayed green.
//
// The portal does rewrite a plugin marker's POM — `org.springframework.boot.gradle.plugin`
// is 0f7de85e… from the portal and 221abccf… from Central — so
// gradle/verification-metadata.xml lists both hashes for the four markers: a
// copy from either source verifies, and a third one cannot.
pluginManagement {
    repositories {
        maven(url = providers.gradleProperty("artifactMirrorUrl").get())
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven(url = providers.gradleProperty("artifactMirrorUrl").get())
        mavenCentral()
    }
}

rootProject.name = "ctt-server"
