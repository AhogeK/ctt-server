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
// Plugin resolution deliberately keeps Gradle's default (the Plugin Portal):
// the portal rewrites a plugin marker's POM, so the same coordinate has
// different bytes there than on Central (`org.springframework.boot.gradle.plugin`
// is 0f7de85e… from the portal, 221abccf… from Central) and pointing it at the
// mirror would either break verification or widen it to two acceptable copies
// of one artifact. Four markers are not what rate-limits; the dependency graph
// is.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven(url = providers.gradleProperty("artifactMirrorUrl").get())
        mavenCentral()
    }
}

rootProject.name = "ctt-server"
