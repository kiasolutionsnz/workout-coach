plugins { kotlin("multiplatform"); id("com.android.kotlin.multiplatform.library"); id("app.cash.sqldelight") }
kotlin {
    android {
        namespace = "tech.kiasolutions.workoutcoach.core"
        compileSdk = 36
        minSdk = 26
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "WorkoutCore"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies { implementation("app.cash.sqldelight:runtime:2.2.1") }
        commonTest.dependencies { implementation(kotlin("test")) }
        androidMain.dependencies { implementation("app.cash.sqldelight:android-driver:2.2.1") }
        iosMain.dependencies { implementation("app.cash.sqldelight:native-driver:2.2.1") }
        jvmMain.dependencies { implementation("app.cash.sqldelight:sqlite-driver:2.2.1") }
    }
}
sqldelight {
    databases {
        create("CoachDatabase") { packageName.set("tech.kiasolutions.workoutcoach.core.persistence.db") }
    }
}
