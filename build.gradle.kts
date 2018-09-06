import org.gradle.internal.os.OperatingSystem;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.util.Scanner

val isUnstable = properties["release"] == null
val commitNumber = Scanner(Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream).next()
version = "dev" + commitNumber +
		"-" + Scanner(Runtime.getRuntime().exec("git rev-parse --short HEAD").inputStream).next()
file("src/resources/version").writeText(version as String)

plugins {
	kotlin("jvm") version "1.2.61"
	application
	id("com.github.johnrengelman.shadow") version "2.0.4"
	id("com.github.ben-manes.versions") version "0.19.0"
	id("com.github.breadmoirai.github-release") version "2.0.1"
}

// source directories
sourceSets {
	getByName("main") {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
	getByName("test").java.srcDir("src/test")
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
	mainClassName = "xerus.monstercat.MainKt"
}

repositories {
	jcenter()
	maven("https://jitpack.io")
	maven("http://maven.bluexin.be/repository/snapshots/")
}

dependencies {
	compile("com.github.Xerus2000", "util", "master-SNAPSHOT")
	compile(kotlin("stdlib-jdk8"))
	compile(kotlin("reflect"))
	
	compile("org.controlsfx", "controlsfx", "8.40.14")
	
	compile("be.bluexin", "drpc4k", "0.6-SNAPSHOT")
	
	compile("org.apache.httpcomponents", "httpmime", "4.5.5")
	compile("com.google.apis", "google-api-services-sheets", "v4-rev527-1.23.0")
	
	testCompile("org.junit.jupiter", "junit-jupiter-api", "5.2.0")
	testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.2.0")
}

kotlin.experimental.coroutines = Coroutines.ENABLE
val jarFile
	get() = "MonsterUtilities-$version.jar"

val MAIN = "_Main"
tasks {
	
	getByName("runShadow").group = MAIN
	getByName("startShadowScripts").group = "distribution"
	
	"run"(JavaExec::class) {
		group = MAIN
		// Usage: gradle run -Dargs="FINE save"
		args = System.getProperty("args", "").split(" ")
	}
	
	"shadowJar"(ShadowJar::class) {
		baseName = "MonsterUtilities"
		classifier = ""
		destinationDir = file(".")
		doLast { file(jarFile).setExecutable(true) }
	}
	
	create<Exec>("release") {
		dependsOn("jar")
		group = MAIN
		val path = file("../monsterutilities-extras/website/downloads/" + if (isUnstable) "unstable" else "latest")
		val pathLatest = path.resolveSibling("latest") // TODO temporary workaround until real release
		doFirst {
			path.writeText(version.toString())
			pathLatest.writeText(version.toString())
			exec { commandLine("git", "tag", version) }
		}
		val s = if (OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no;
			open -u ${properties["credentials.ftp"]} -e $s"
			cd /www/downloads; ${if (properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			cd ./files; put $jarFile;
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	replace("jar", Delete::class).run {
		group = MAIN
		dependsOn("shadowJar")
		setDelete(file(".").listFiles { f -> f.name.run { startsWith("${rootProject.name}-") && endsWith("jar") && this != jarFile } })
	}
	
	"test"(Test::class) {
		useJUnitPlatform()
	}
	
}

githubRelease {
	setToken(property("github.token")?.toString())
	setOwner("Xerus2000")
	setReleaseAssets(jarFile)
	
	setTagName(if(isUnstable) "dev$commitNumber" else version.toString())
	setBody(properties["m"]?.toString())
	setReleaseName("Dev $commitNumber" + if (properties["n"] != null) " - ${properties["n"]}" else "")
	setPrerelease(isUnstable)
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
