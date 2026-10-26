package io.xenxo.padel

/**
 * Reglas del pádel, sin Android: el estado se calcula siempre "repitiendo" la lista de puntos jugados.
 * Deshacer = quitar el último punto; guardar/recuperar un partido = guardar la lista (ver PadelStore).
 *
 * - Juegos 15-30-40 con ventaja o punto de oro (en 40-40, el siguiente punto gana el juego).
 * - Sets a 6 con 2 de diferencia; en 6-6, tie-break a 7 con 2 de diferencia (el set queda 7-6).
 * - Al mejor de 3 sets; opcionalmente, en 1-1 se juega un super tie-break a 10 en vez del tercer set.
 * - Saque: alterna cada juego. En los tie-breaks saca 1 punto quien le toca y luego cada 2 puntos;
 *   tras el tie-break saca el equipo que restó el primer punto.
 * - Cambio de lado: tras los juegos impares de cada set y cada 6 puntos de un tie-break.
 */
class PadelMatch(val config: Config, points: List<Team> = emptyList()) {

    enum class Team { US, THEM;
        val other get() = if (this == US) THEM else US
    }

    data class Config(val goldenPoint: Boolean, val superTieBreak: Boolean, val firstServer: Team)

    /** Set terminado. [tieBreakLoser] = puntos del perdedor en el tie-break, si lo hubo ("7-6(5)"). */
    data class SetScore(val us: Int, val them: Int, val tieBreakLoser: Int? = null, val superTieBreak: Boolean = false) {
        override fun toString() = when {
            superTieBreak -> "[$us-$them]"
            tieBreakLoser != null -> "$us-$them($tieBreakLoser)"
            else -> "$us-$them"
        }
    }

    /** Qué cambió con el último punto, para vibrar o avisar. */
    enum class Event { POINT, GAME, SET, MATCH }

    private val history = points.toMutableList()
    val points: List<Team> get() = history

    // ---- estado calculado ----
    var sets = listOf<SetScore>(); private set
    var games = IntArray(2); private set          // juegos del set en curso [US, THEM]
    var pts = IntArray(2); private set            // puntos del juego o del tie-break en curso
    var inTieBreak = false; private set
    var inSuperTieBreak = false; private set
    var server = config.firstServer; private set
    var winner: Team? = null; private set
    /** Tras el último punto toca cambiar de lado. */
    var changeEnds = false; private set
    var lastEvent = Event.POINT; private set

    init { replay() }

    fun point(t: Team): Event {
        if (winner != null) return Event.MATCH
        history += t
        replay()
        return lastEvent
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        history.removeAt(history.size - 1)
        replay()
        return true
    }

    val setsWon: IntArray get() = IntArray(2).also { w ->
        sets.forEach { s -> if (s.us > s.them) w[0]++ else w[1]++ }
    }

    /** Puntos del juego en curso tal como se cantan: "0", "15", "30", "40", "AD" o el número en tie-break. */
    fun pointLabel(t: Team): String {
        val mine = pts[t.ordinal]; val theirs = pts[t.other.ordinal]
        if (inTieBreak) return mine.toString()
        if (mine >= 3 && theirs >= 3) return if (mine > theirs) "AD" else "40"
        return LABELS[mine.coerceAtMost(3)]
    }

    /** 40-40 con punto de oro: el siguiente punto decide el juego. */
    val isGoldenPoint get() = config.goldenPoint && !inTieBreak && pts[0] == 3 && pts[1] == 3

    /** "6-4 3-6 7-6(5)" con los sets terminados (y el en curso si [withCurrent]). */
    fun resultText(withCurrent: Boolean = false): String {
        val parts = sets.map { it.toString() }.toMutableList()
        if (withCurrent && winner == null && (games[0] + games[1] + pts[0] + pts[1]) > 0) {
            parts += if (inSuperTieBreak) "[${pts[0]}-${pts[1]}]" else "${games[0]}-${games[1]}"
        }
        return parts.joinToString(" ")
    }

    private fun replay() {
        val done = mutableListOf<SetScore>()
        val g = IntArray(2); val p = IntArray(2)
        var tb = false; var stb = false
        var srv = config.firstServer
        var tbFirst = srv
        var win: Team? = null
        var event = Event.POINT
        var ends = false

        fun wonSets(t: Team) = done.count { (if (t == Team.US) it.us > it.them else it.them > it.us) }

        fun closeSet(s: SetScore, t: Team) {
            done += s
            g.fill(0); p.fill(0); tb = false; stb = false
            event = Event.SET
            if (wonSets(t) == 2) { win = t; event = Event.MATCH; return }
            if (config.superTieBreak && wonSets(Team.US) == 1 && wonSets(Team.THEM) == 1) {
                tb = true; stb = true; tbFirst = srv
            }
        }

        for (t in history) {
            if (win != null) break
            val i = t.ordinal; val o = t.other.ordinal
            p[i]++
            event = Event.POINT
            ends = false
            if (tb) {
                val target = if (stb) 10 else 7
                if (p[i] >= target && p[i] - p[o] >= 2) {
                    srv = tbFirst.other
                    if (stb) closeSet(SetScore(p[0], p[1], superTieBreak = true), t)
                    else {
                        g[i]++
                        closeSet(SetScore(g[0], g[1], tieBreakLoser = p[o]), t)
                    }
                    ends = true
                } else {
                    val n = p[0] + p[1]
                    srv = if (((n + 1) / 2) % 2 == 0) tbFirst else tbFirst.other
                    ends = n % 6 == 0
                }
                continue
            }
            val gameWon = if (config.goldenPoint) p[i] >= 4 else p[i] >= 4 && p[i] - p[o] >= 2
            if (!gameWon) continue
            g[i]++; p.fill(0)
            srv = srv.other
            event = Event.GAME
            val total = g[0] + g[1]
            if (g[i] >= 6 && g[i] - g[o] >= 2) {
                closeSet(SetScore(g[0], g[1]), t)
                ends = total % 2 == 1
            } else {
                if (g[0] == 6 && g[1] == 6) { tb = true; tbFirst = srv }
                ends = total % 2 == 1
            }
        }

        sets = done; games = g; pts = p
        inTieBreak = tb; inSuperTieBreak = stb
        server = srv; winner = win
        lastEvent = if (history.isEmpty()) Event.POINT else event
        changeEnds = ends && history.isNotEmpty() && win == null
    }

    companion object {
        private val LABELS = arrayOf("0", "15", "30", "40")
    }
}
