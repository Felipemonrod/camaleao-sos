pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "CamaleaoSOS"
include(":app")
include(":sms-probe")
project(":sms-probe").projectDir = file("tools/sms-probe")
