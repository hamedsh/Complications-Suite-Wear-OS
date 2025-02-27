plugins {
    id ("org.jetbrains.kotlin.android") version("1.9.10") apply false
    id ("com.android.library") version "8.1.3" apply false
    id ("com.google.dagger.hilt.android") version "2.47" apply false
}

tasks.register("clean", Delete::class){
    delete(rootProject.buildDir)
}

buildscript {
    dependencies {
        classpath ("com.android.tools.build:gradle:8.2.0")
        classpath ("org.jetbrains.kotlin:kotlin-serialization:1.9.10")
    }
    repositories {
        google()
    }
}