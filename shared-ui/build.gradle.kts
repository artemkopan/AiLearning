plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
    jvm()
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":shared-contract"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.kermit)
                implementation(libs.lifecycle.viewmodel.compose)
                api(libs.koin.core)
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                implementation(libs.koin.annotations)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.koin.ksp.compiler)
    add("kspJvm", libs.koin.ksp.compiler)
    add("kspJs", libs.koin.ksp.compiler)
}

tasks.matching { it.name == "compileKotlinJvm" || it.name == "compileKotlinJs" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.matching { it.name == "kspKotlinJvm" || it.name == "kspKotlinJs" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
