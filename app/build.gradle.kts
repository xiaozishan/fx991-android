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
        versionCode = 114
        versionName = "1.12.0-symbolic"

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

    signingConfigs {
        // debug 变体同样要求 v3 关闭（出货包验收卡 v3=false）；v1+v2 保持
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
        }
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                // 手写解析 key=value（密码不含 = 号；避免在 Kotlin DSL 里引 java.util 的麻烦）
                val props = propsFile.readLines().mapNotNull {
                    val i = it.indexOf('=')
                    if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
                }.toMap()
                storeFile = rootProject.file(props["storeFile"]!!)
                storePassword = props["storePassword"]!!
                keyAlias = props["keyAlias"]!!
                keyPassword = props["keyPassword"]!!
            }
            // ColorOS 安装器解析不了 v3 签名块（报「解析失败：安装包没有签名文件」整包拒装）；
            // v1（JAR）+ v2 保留，v3 一律关闭（2026-10-01 踩实；验收：apksigner verify -v 必须 v2=true / v3=false）
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
        }
    }

    buildTypes {
        debug {
            // ColorOS 拦 debuggable=true 的包；debug 变体保留 debug 密钥签名（天然带 v1+v2+v3，
            // 你手机 v1.6~v1.10 全装上了就是证据），只把 debuggable 关掉（2026-10-01 实测 release 自定义签名只出 v2/v3，ColorOS 报「没有签名文件」）
            isDebuggable = false
        }
        release {
            // 2026-10-01：debug 包带 debuggable=true，ColorOS 等新系统从文件管理器安装会拦。
            // release 变体：非 debuggable + 专用 release 密钥签名（密钥在 keystore.properties，gitignore 挡住）。
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
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
