plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("app.cash.sqldelight") version "2.0.2"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        // Git4Idea's GitRepository extends com.intellij.dvcs.repo.Repository which lives in this module;
        // without it the supertype is invisible on the compile classpath.
        bundledModule("intellij.platform.vcs.dvcs.impl")
        pluginVerifier()
        zipSigner()
        instrumentationTools()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sqldelight {
    databases {
        create("GitInsightDatabase") {
            packageName.set("com.power.gitinsight.infra.storage.db")
            // SQLite 3.24+ required for ON CONFLICT ... DO UPDATE; pin a recent dialect.
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.0.2")
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // No upper bound: compatible with all IDEs from sinceBuild onwards, so a new major
            // IDE release never auto-marks the plugin incompatible. `provider { null }` is the
            // documented way to unset until-build (omitting it would default to MAJOR.*).
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // recommended() resolves the IDEs JetBrains currently marks as recommended, which includes
            // 2025.3 — not yet published to the repository, so the download fails. Pin the verifier to
            // the compiled platform (guaranteed resolvable) so verifyPlugin runs reliably; bump/add
            // newer versions here once they are available in the repository.
            ide(
                org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity,
                providers.gradleProperty("platformVersion").get(),
            )
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    test {
        useJUnitPlatform()
    }
}
