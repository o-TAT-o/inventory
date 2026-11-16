package io.github.otato.traininglog.data.testing

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.domain.IdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.util.Properties

/** テスト用に、メモリ上の SQLite でデータ層を作る（外部キーも本物と同じく有効）。 */
object TestDatabase {
    /** メモリ上の SQLite のドライバー（テーブルは、まだ作っていない）。 */
    fun newDriver(): SqlDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })

    fun create(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        clock: Clock = Clock.systemDefaultZone(),
    ): DataModule {
        val driver = newDriver()
        DataModule.schema.create(driver)
        return DataModule(driver, dispatcher, clock)
    }

    /** "id-0", "id-1", ... と順に返す ID の作り方。 */
    fun sequentialIds(prefix: String = "id"): IdGenerator {
        var counter = 0
        return IdGenerator { "$prefix-${counter++}" }
    }
}
