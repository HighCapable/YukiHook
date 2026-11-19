import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.dokka)
    alias(libs.plugins.maven.publish)
}

group = gropify.project.groupName
version = gropify.project.yukihook.bom.version

android {
    namespace = gropify.project.groupName
    compileSdk = gropify.project.android.compileSdk

    defaultConfig {
        minSdk = gropify.project.android.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint { checkReleaseBuilds = false }
}

configure<LibraryExtension> {
    sourceSets.named("main") {
        kotlin.directories.add("src/share/kotlin")
    }
}

dependencies {
    lintPublish(projects.yukihookCoreLint)

    compileOnly(projects.yukihookRuntimeStub)
    implementation(libs.hiddenapibypass)

    implementation(platform(libs.kavaref.bom))
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.android)
    implementation(libs.kavaref.extension)

    implementation(platform(libs.betterandroid.android.bom))
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.betterandroid.system.extension)

    api(libs.androidx.lifecycle.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(projects.yukihookRuntimeXposed82)
}