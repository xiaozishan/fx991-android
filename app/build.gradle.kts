plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.paimon.fx991"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.paimon.fx991"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.11.0-inline"

        ndk {
            // 批次 F 起 ML Kit 带进 4 个 ABI 的 .so（合计约 41 MB），全打进去 APK 会胀到 53 MB。
            // 现代手机都是 arm64，默认只留 arm64-v8a（约省 30 MB）。
            // 需要多架构（如本地 x86_64 模拟器）时加 -PallAbi。
            abiFilters += if (project.hasProperty("allAbi")) {
                listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            } else {
                listOf("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // 批次 F（本地离线 OCR）：本工程引入的第一个第三方依赖（旅行者明确要求的例外）。
    // bundled 版：识别模型随 APK 发布，不依赖 Google Play 服务，完全离线可用。
    // 中文模型本身兼认拉丁字母与数字；拉丁模型作为整图空白时的兜底。
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
