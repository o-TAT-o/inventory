// アプリ本体: 画面（Compose）、ViewModel、ナビゲーション、Android 固有の処理（通知・振動など）、依存の組み立て。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.otato.traininglog"
    // 最新の Compose / Navigation が compileSdk 37 以上を求める。targetSdk（動作の取り扱い）とは別に上げられる
    compileSdk = 37

    defaultConfig {
        // applicationId は仮。アプリ名が決まり、公開する前に確定する（公開後は変えられない）
        applicationId = "io.github.otato.traininglog"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // デバッグ用の鍵はリポジトリに置く（誰が作っても同じ署名になり、上書きインストールできる）。公開用の鍵は別に作り、コミットしない
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 公開用の鍵ができるまでは、サイズの実測と動作確認のためにデバッグの鍵で署名する
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqldelight.android.driver)

    testImplementation(testFixtures(project(":data")))
    // Android のモジュールでは、テストの枠組み（JUnit4）を自動では選ばないので、明示する
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
