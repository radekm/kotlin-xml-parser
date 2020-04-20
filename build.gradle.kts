plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.70"

    `java-library`
}

repositories {
    jcenter()
}

dependencies {
    // Align versions of all Kotlin components.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
