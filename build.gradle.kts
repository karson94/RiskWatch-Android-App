buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.1")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
    }
}
plugins {
    id("com.android.application") version "8.7.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}