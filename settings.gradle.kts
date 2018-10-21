pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://jitpack.io")
	}
	resolutionStrategy {
		eachPlugin {
			if(requested.id.namespace == "com.alkimiapps" && requested.version == "0.4")
				useModule("com.github.Xerus2000:gradle-dplink-plugin:kotlin-SNAPSHOT")
		}
	}
}

rootProject.name = "MonsterUtilities"