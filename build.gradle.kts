import com.github.breadmoirai.githubreleaseplugin.GithubReleaseTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.IOException
import java.util.*
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.util.Scanner
import com.alkimiapps.gradle.plugin.dplink.DplinkTask

val isUnstable = properties["release"] == null
var commitNumber: String = ""
try {
	commitNumber = Scanner(Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream).next()
	version = "dev" + commitNumber +
		"-" + Scanner(Runtime.getRuntime().exec("git rev-parse --short HEAD").inputStream).next()
} catch(e: IOException) {
	println("Encountered an exception while determining the version - $e\nThe most likely cause is that git is not installed!")
	version = "self-compiled"
}
file("src/resources/version").writeText(version as String)

plugins {
	kotlin("jvm") version "1.3.41"
	application
	id("com.github.johnrengelman.shadow") version "5.1.0"
	id("com.github.breadmoirai.github-release") version "2.2.9"
	id("com.alkimiapps.gradle-dplink-plugin") version "0.4"
	id("com.github.ben-manes.versions") version "0.21.0"
}

// source directories
sourceSets {
	main {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
	test {
		java.srcDir("src/test")
	}
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
	mainClassName = "xerus.monstercat.MainKt"
}

repositories {
	jcenter()
	maven("https://jitpack.io")
}

dependencies {
	implementation(kotlin("reflect"))
	
	val os = org.gradle.internal.os.OperatingSystem.current()
	val classifier = when {
		os.isLinux -> "linux"
		os.isMacOsX -> "mac"
		os.isWindows -> "win"
		else -> throw Exception("Unable to determine javafx dependency classifier due to unfamiliar system=$os")
	}
	implementation("org.openjfx:javafx-base:$version:$classifier")
	implementation("org.openjfx:javafx-controls:$version:$classifier")
	implementation("org.openjfx:javafx-graphics:$version:$classifier")
	implementation("org.openjfx:javafx-media:$version:$classifier")
	
	implementation("com.github.Xerus2000.util", "javafx", "61043c3eb09a7f0a3d3b2227bf2dac463958d237")
	implementation("org.controlsfx", "controlsfx", "11.0.0")
	
	api("org.slf4j", "slf4j-api", "1.8.0-beta4")
	implementation("ch.qos.logback", "logback-classic", "1.3.+")
	implementation("io.github.microutils", "kotlin-logging", "1.6.+")
	
	implementation("be.bluexin", "drpc4k", "0.9")
	implementation("org.apache.httpcomponents", "httpmime", "4.5.+")
	implementation("com.google.apis", "google-api-services-sheets", "v4-rev20190508-1.30.1")
	
	val junitVersion = "5.5.0"
	testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
	testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
	testImplementation("io.kotlintest", "kotlintest-runner-junit5", "3.3.3")
}

val jarFile
	get() = "$name-$version.jar"

githubRelease {
	tagName(version.toString())
	body(project.properties["m"]?.toString() ?: "")
	releaseName("Dev $commitNumber" + project.properties["n"]?.let { " - $it" }.orEmpty())
	
	prerelease(isUnstable)
	releaseAssets(jarFile)
	owner("Xerus2000")
	
	token(project.properties["github.token"]?.toString())
}

val MAIN = "_main"
tasks {
	
	arrayOf(run.get(), runShadow.get()).forEach {
		it.group = MAIN
		it.args = System.getProperty("args", "--loglevel debug").split(" ")
	}
	
	val shadowJar by getting(ShadowJar::class) {
		group = MAIN
		archiveClassifier.set("")
		destinationDirectory.set(file("."))
		doFirst {
			destinationDirectory.get().asFile.listFiles()!!.forEach {
				if(it.name.endsWith("jar"))
					it.delete()
			}
		}
		doLast {
			outputs.files.singleFile.setExecutable(true)
		}
	}
	
	val githubRelease by getting(GithubReleaseTask::class) {
		dependsOn(shadowJar)
	}
	
	val websiteRelease by creating(Exec::class) {
		dependsOn(shadowJar)
		
		val path = file("../monsterutilities-extras/website/downloads/" + if(isUnstable) "unstable" else "latest")
		val pathLatest = path.resolveSibling("latest") // TODO temporary workaround until real release
		doFirst {
			path.writeText(version.toString())
			pathLatest.writeText(version.toString())
			println("Uploading release to server...")
		}
		
		val s = if(OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no; set cmd:fail-exit true;
			open -u ${project.properties["credentials.ftp"]} -e $s"
			cd /www/downloads/files && put $jarFile;
			cd /www/downloads && ${if(project.properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	val release by creating {
		dependsOn(websiteRelease, githubRelease)
		group = MAIN
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	test {
		useJUnitPlatform()
	}
	
	withType<DplinkTask> {
		scriptsLocation = "monsterutilities"
		libs = files("build/libs", configurations.default)
		mainClassName = application.mainClassName
		executableJarName = jarFile
	}
	
	withType<Jar> {
		manifest {
			attributes["Main-Class"] = application.mainClassName
			attributes["Class-Path"] = configurations.default.get().joinToString(" ") { it.name }
		}
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
