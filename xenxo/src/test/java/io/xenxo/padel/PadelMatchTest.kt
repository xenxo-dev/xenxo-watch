package io.xenxo.padel

import io.xenxo.padel.PadelMatch.Config
import io.xenxo.padel.PadelMatch.Event
import io.xenxo.padel.PadelMatch.Team.THEM
import io.xenxo.padel.PadelMatch.Team.US
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadelMatchTest {

    private fun match(golden: Boolean = false, stb: Boolean = false) = PadelMatch(Config(golden, stb, US))
    private fun PadelMatch.win(t: PadelMatch.Team, n: Int = 1) = repeat(n) { point(t) }
    private fun PadelMatch.game(t: PadelMatch.Team) = win(t, 4)

    @Test fun pointLabels() {
        val m = match()
        assertEquals("0", m.pointLabel(US))
        m.win(US); assertEquals("15", m.pointLabel(US))
        m.win(US); assertEquals("30", m.pointLabel(US))
        m.win(US); assertEquals("40", m.pointLabel(US))
    }

    @Test fun advantageNeedsTwoPoints() {
        val m = match()
        m.win(US, 3); m.win(THEM, 3)
        assertEquals("40", m.pointLabel(US)); assertEquals("40", m.pointLabel(THEM))
        m.win(US); assertEquals("AD", m.pointLabel(US)); assertEquals(0, m.games[0])
        m.win(THEM); assertEquals("40", m.pointLabel(US))   // vuelta a iguales
        m.win(US); m.win(US)
        assertEquals(1, m.games[0])
    }

    @Test fun goldenPointDecidesAt4040() {
        val m = match(golden = true)
        m.win(US, 3); m.win(THEM, 3)
        assertTrue(m.isGoldenPoint)
        assertEquals(Event.GAME, m.point(THEM))
        assertEquals(1, m.games[1])
    }

    @Test fun serveAlternatesEachGame() {
        val m = match()
        assertEquals(US, m.server)
        m.game(THEM); assertEquals(THEM, m.server)
        m.game(THEM); assertEquals(US, m.server)
    }

    @Test fun setToSixWithTwoClear() {
        val m = match()
        repeat(5) { m.game(US); m.game(THEM) }        // 5-5
        m.game(US); assertEquals(0, m.sets.size)       // 6-5
        m.game(US)                                     // 7-5
        assertEquals("7-5", m.resultText())
    }

    @Test fun tieBreakAtSixAll() {
        val m = match()
        repeat(6) { m.game(US); m.game(THEM) }        // 6-6
        assertTrue(m.inTieBreak)
        val tbFirst = m.server
        m.win(US); assertEquals(tbFirst.other, m.server)   // 1 punto y luego cada 2
        m.win(US); assertEquals(tbFirst.other, m.server)
        m.win(US); assertEquals(tbFirst, m.server)
        m.win(THEM, 5)                                       // 3-5
        m.win(US, 4)                                         // 7-5
        assertEquals("7-6(5)", m.resultText())
        assertEquals(tbFirst.other, m.server)
    }

    @Test fun bestOfThree() {
        val m = match()
        repeat(6) { m.game(US) }
        repeat(6) { m.game(THEM) }
        assertNull(m.winner)
        repeat(6) { m.game(US) }
        assertEquals(US, m.winner)
        assertEquals("6-0 0-6 6-0", m.resultText())
        assertEquals(Event.MATCH, m.lastEvent)
    }

    @Test fun superTieBreakInsteadOfThirdSet() {
        val m = match(stb = true)
        repeat(6) { m.game(US) }
        repeat(6) { m.game(THEM) }
        assertTrue(m.inSuperTieBreak)
        m.win(US, 9); m.win(THEM, 9)                         // 9-9
        m.win(US); assertNull(m.winner)                      // 10-9: falta diferencia de 2
        m.win(US)
        assertEquals(US, m.winner)
        assertEquals("6-0 0-6 [11-9]", m.resultText())
    }

    @Test fun undoRestoresPreviousState() {
        val m = match()
        m.game(US)
        assertEquals(1, m.games[0])
        assertTrue(m.undo())
        assertEquals(0, m.games[0]); assertEquals("40", m.pointLabel(US))
        val restored = PadelMatch(m.config, m.points)
        assertEquals("40", restored.pointLabel(US))
    }

    @Test fun changeEndsAfterOddGames() {
        val m = match()
        m.game(US); assertTrue(m.changeEnds)
        m.game(US); assertFalse(m.changeEnds)
        m.game(US); assertTrue(m.changeEnds)
    }

    @Test fun noPointsAfterMatchEnds() {
        val m = match()
        repeat(12) { m.game(US) }
        val n = m.points.size
        m.point(THEM)
        assertEquals(n, m.points.size)
    }
}
