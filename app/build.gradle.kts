plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.tonbo_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tonbo_app"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // --- 核心修改部分：解決文件衝突 ---
    packaging {
        resources {
            // 解決 common.properties 衝突
            pickFirsts.add("common.properties")
            pickFirsts.add("META-INF/common.properties")

            // 解決你最新的報錯：META-INF/DEPENDENCIES 衝突
            pickFirsts.add("META-INF/DEPENDENCIES")

            // 預防性排除其他常見的元數據衝突文件
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/ASL2.0")
        }
    }
}

dependencies {
    // Kotlin標準庫
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // CameraX dependencies for camera functionality (使用更穩定的版本)
    implementation("androidx.camera:camera-core:1.1.0")
    implementation("androidx.camera:camera-camera2:1.1.0")
    implementation("androidx.camera:camera-lifecycle:1.1.0")
    implementation("androidx.camera:camera-view:1.1.0")

    // TensorFlow Lite for YOLO model inference
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // ONNX Runtime (可選：用於 ONNX 模型)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // Google ML Kit for OCR text recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    // Google ML Kit Image Labeling (本地模型)
    implementation("com.google.mlkit:image-labeling:17.0.7")

    // Google ML Kit Object Detection（可選，網絡不可用時可註釋）
    // implementation("com.google.mlkit:object-detection:17.0.1")
    // implementation("com.google.mlkit:object-detection-custom:17.0.1")

    // Google Location Services (僅用於緊急求助時發送位置)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 高德 Android 導航 SDK（3D 地圖），版本格式為 X.Y.Z_3dmapX.Y.Z，從 Maven Central 解析
    implementation("com.amap.api:navi-3dmap:10.0.800_3dmap10.0.800")

    // Agora RTC SDK for video calling
    implementation("io.agora.rtc:full-sdk:4.3.0")

    // OkHttp for HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // 阿里云OSS SDK for file upload
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}