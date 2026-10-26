package io.xenxo.intervals

enum class IntervalPhase {
    IDLE, PREPARE, WORK, REST, FINISHED
}

data class IntervalPreset(
    val id: String,
    val name: String,
    val workSeconds: Int,
    val restSeconds: Int,
    val totalRounds: Int
) {
    companion object {
        val TABATA = IntervalPreset("tabata", "Tabata 20/10", 20, 10, 8)
        val HIIT_30 = IntervalPreset("hiit30", "HIIT 30/30", 30, 30, 10)
        val HIIT_45 = IntervalPreset("hiit45", "HIIT 45/15", 45, 15, 8)
        val EMOM = IntervalPreset("emom", "EMOM (1 min)", 60, 0, 10)

        val ALL = listOf(TABATA, HIIT_30, HIIT_45, EMOM)
    }
}
