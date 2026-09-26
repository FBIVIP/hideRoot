plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.refine)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

val configVerCode: Int by rootProject.extra
val serviceVerCode: Int by rootProject.extra
val minBackupVerCode: Int by rootProject.extra
val appPackageName: String by rootProject.extra
val appId: String by rootProject.extra
val appVerName: String by rootProject.extra
val appVerCode: Int by rootProject.extra

android {
    namespace = "$appPackageName.common"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("int", "CONFIG_VERSION", configVerCode.toString())
        buildConfigField("int", "SERVICE_VERSION", serviceVerCode.toString())
        buildConfigField("int", "MIN_BACKUP_VERSION", minBackupVerCode.toString())
        // Must equal the manager applicationId (appId): Constants.PROVIDER_AUTHORITY
        // is "${APP_PACKAGE_NAME}.ServiceProvider" and has to match the manifest's
        // "${applicationId}.ServiceProvider" for the zygote <-> app IPC to bind.
        buildConfigField("String", "APP_PACKAGE_NAME", "\"$appId\"")
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVerName\"")
        buildConfigField("int", "APP_VERSION_CODE", appVerCode.toString())
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    compileOnly(projects.stub)
}
