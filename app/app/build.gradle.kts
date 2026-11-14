import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서버 주소는 저장소에 넣지 않는다: local.properties 의 snowbot.server (예: https://<서버-IP>.sslip.io)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.snowbot.hub"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.snowbot.hub"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SERVER_URL", "\"${localProps.getProperty("snowbot.server", "")}\"")
    }

    // 릴리스 서명 키도 저장소에 넣지 않는다: local.properties 의 release.store / release.password / release.alias
    val releaseStore = localProps.getProperty("release.store")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = localProps.getProperty("release.password")
                keyAlias = localProps.getProperty("release.alias", "snowbot")
                keyPassword = localProps.getProperty("release.password")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")   // Pleos 차량 데이터 (조회만 — CAR_* 권한은 신청 불필요)
    implementation("ai.pleos.playground:NaviHelper:3.0.5")   // 목적지까지 남은 거리·시간, 앞 정체, 내비 화면 열기 (NAVI_* 권한도 신청 불필요)
    // Pleos 음성 SDK — 스노봇 듣기·말하기 (PleosVoice, 차 안 엔진)
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")   // 단위 시험에서 안드로이드 JSONObject 대신
}
