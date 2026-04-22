plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "de.majuwa.android"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    api(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "de.majuwa.android"
            artifactId = "login-flow-nextcloud"
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set("Login Flow for Nextcloud")
                description.set("Kotlin JVM library implementing the Nextcloud Login Flow v2 protocol")
                url.set("https://github.com/majuwa/login-flow-nextcloud")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/majuwa/login-flow-nextcloud")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.token").orNull
            }
        }
    }
}
