pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google.dagger")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "forkly"
include(":app")
include(":core:model")
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":core:data")
include(":core:testing")
include(":feature:home")
include(":feature:search")
include(":feature:readme")
include(":feature:details")
include(":feature:downloads")
include(":feature:installation")
include(":feature:settings")
