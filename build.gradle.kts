plugins {
    id("com.android.application") version "8.4.1" apply false
    id("com.android.library") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    // room compiler で使用。
    // ksp のバージョンは、kotlin_version と同じ世代のものを指定する必要がある。
    // https://github.com/google/ksp/releases で確認する。
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
}

