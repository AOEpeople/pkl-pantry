import org.pkl.core.Version
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

plugins {
    kotlin("jvm").version(libs.versions.kotlin)
    alias(libs.plugins.pkl)
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.pklCore)
    implementation(libs.pklDoc)
}

// True if environment variable CI is set
val isInCi = System.getenv("CI") != null

// allow to pass
val envSkipPublishCheck = System.getenv("CI_SKIP_PUBLISH_CHECK") != null

// The git repo url
val repositoryUrl = "https://github.com/AOEPeople/pkl-pantry"

// The Github API endpoint for the repo
val repositoryApiUrl = repositoryUrl.replace(Regex("github.com/"), "api.github.com/repos/")

// The package URI prefix (points to github pages)
val packageUriPrefix = "package://opensource.aoe.com/pkl-pantry/packages"

// The output directory (gradle default)
val outputDir = layout.buildDirectory

// List of packages
val projectDirs: List<File> =
    Files.list(Path.of("packages"))
        .filter { it.isDirectory() }
        .map { it.toFile() }
        .toList()

// PKL Gradle-Plugin configuration
pkl {
    project {
        resolvers {
            register("resolveProjects") {
                projectDirectories.from(projectDirs)
            }
        }
        packagers {
            register("createPackages") {
                projectDirectories.from(projectDirs)
                outputPath.set(outputDir.dir("generated/packages/%{name}/%{version}"))
                skipPublishCheck = if (envSkipPublishCheck) true else !isInCi
            }
        }
    }
    pkldocGenerators {
        register("pkldoc") {
            outputDir = layout.projectDirectory.dir("docs")
            sourceModules.addAll(
                fileTree("docs/packages")
                    .matching {
                        include("*@*")

                        // TODO: Remove this exclusion once package doc generation works for them
                        exclude("com.gitlab.ci.golang@*")
                        exclude("com.gitlab.ci.docker@*")
                    }
                    .map {
                        "${packageUriPrefix}/${it.name.substring(it.name.lastIndexOf("/") + 1)}"
                    }
                    .toList()
            )
        }
    }
}

// Add pkldoc task to documentation group
val pklDocs = tasks.named("pkldoc") {
    group = "documentation"
}

// Build task depends on createReleases
tasks.build {
    dependsOn(prepareReleases)
}

// Task from PKL Gradle-Plugin to be added to 'build' group
val resolveProjects = tasks.named("resolveProjects") {
    group = "build"
    inputs.files(fileTree("packages/") { include("PklProject") })
    outputs.files(fileTree("packages/") { include("PklProject.deps.json") })
}

// Task from PKL Gradle-Plugin to be added to 'build' group
val createPackages = tasks.named("createPackages") {
    group = "build"
    dependsOn.add(resolveProjects)
}

// Task to generate packages and move the files to the right places
val prepareReleases by tasks.registering {
    group = "build"
    dependsOn(createPackages)
    inputs.files(projectDirs)

    doLast {
        val releaseDir = file(outputDir.dir("releases"))
        releaseDir.deleteRecursively()
        for (i in projectDirs.indices) {
            val dir = projectDirs[i]
            println(dir.name)
            val allVersions = file(outputDir.dir("generated/packages/${dir.name}")).list()
            if (allVersions == null) {
                println("∅")
                continue
            }
            val latestVersion = allVersions.map(Version::parse).sortedWith(Version.comparator()).last()
            val pkg = "${dir.name}@$latestVersion"
            print("$pkg: ")

            for (artifact in file(outputDir.dir("generated/packages/${dir.name}/$latestVersion")).listFiles()!!) {
                artifact.copyTo(releaseDir.resolve("${pkg}/${artifact.name}"), true)

                if (isInCi && !artifact.path.endsWith("zip") && !artifact.path.endsWith("sha256")) {
                    artifact.copyTo(projectDir.resolve("docs/packages/${artifact.name}"), true)
                }
            }

            println("✅")
        }
    }
}
