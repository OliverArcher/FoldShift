// Root build script. Plugin versions are declared in the libs.versions.toml
// via Gradle version catalogs once we add them; for now we keep simple block
// declarations so the project compiles with no extra wiring.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}