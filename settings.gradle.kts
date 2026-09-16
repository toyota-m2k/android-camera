pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
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
        mavenLocal()
        maven (url="https://jitpack.io")
    }
}

rootProject.name = "TryCamera"
include(":libCamera")
// jitpackではライブラリ(libCamera)だけ公開できればよいので、secureCameraはincludeしない。
// （JITPACK環境変数はjitpackのビルド時のみ true が設定される）
// また、secureCameraの実フォルダが SecureCamera （Sが大文字）になっていて、
// Linux(jitpack)ではそのままだとエラーになるため、projectDirで実フォルダを明示している。
if (System.getenv("JITPACK") == null) {
    include(":monitor")
    include(":secureCamera")
    project(":secureCamera").projectDir = file("SecureCamera")
}
