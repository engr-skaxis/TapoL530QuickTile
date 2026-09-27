plugins {
    id("com.android.application") version "8.6.1" apply false
}

android {
    namespace = "com.bbbun.tapol530"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bbbun.tapol530"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
