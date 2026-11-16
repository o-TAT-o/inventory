import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// データ層: SQLDelight のスキーマ、リポジトリの実装、初期データ。Android には依存しない
// （Android 用のドライバーは app が渡す。テストは JVM の SQLite で動かす）。
plugins {
    alias(libs.plugins.kotlin.jvm)
    // 書き出しファイル（JSON）の読み書き
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    // メモリ上のデータベースを作るテスト用の部品を、app のテストからも使えるようにする
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sqldelight {
    databases {
        create("TrainingLogDatabase") {
            packageName.set("io.github.otato.traininglog.data.db")
            dialect(libs.sqldelight.dialect.sqlite318)
        }
    }
}

dependencies {
    api(project(":domain"))
    // DataModule が SqlDriver / SqlSchema を公開するので、app からも見えるようにする
    api(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.sqldelight.sqlite.driver)

    testImplementation(testFixtures(project(":data")))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.turbine)
}
