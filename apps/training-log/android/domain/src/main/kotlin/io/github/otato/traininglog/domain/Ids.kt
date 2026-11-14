package io.github.otato.traininglog.domain

import java.util.UUID

/** ID の作り方。ID は UUID にする（将来、別の端末やバックアップと突き合わせても番号がぶつからない。6.2）。テストでは固定の値を返す実装に差し替える。 */
fun interface IdGenerator {
    fun next(): String
}

object UuidGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}
