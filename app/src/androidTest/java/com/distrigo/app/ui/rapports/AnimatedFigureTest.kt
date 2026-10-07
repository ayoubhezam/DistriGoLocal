package com.distrigo.app.ui.rapports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A figure that counts to its new value: not on its first showing, through the values in between, to
 * the new value exactly — an amount of hundreds of millions included — and read once by TalkBack.
 */
@RunWith(AndroidJUnit4::class)
class AnimatedFigureTest {

    @get:Rule
    val compose = createComposeRule()

    private val money = MoneyFormatter.of(MoneyFormat.SPACES)
    private val nb = ' '

    /** The one text the figure shows now. */
    private fun shown(): String {
        val nodes = compose.onAllNodesWithText("DA", substring = true).fetchSemanticsNodes()
        assertEquals(compose.onRoot().printToString(), 1, nodes.size)
        return nodes.single().config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
    }

    @Test
    fun itShowsItsFirstValueAtOnce() {
        compose.setContent { AnimatedFigure(money.figure(1500.0), fontSize = 20.sp) }
        compose.onNodeWithText("1${nb}500,00${nb}DA").assertExists()
    }

    @Test
    fun itCountsThroughTheValuesBetweenToTheNewOneExactly() {
        var amount by mutableStateOf(100.0)
        compose.setContent { AnimatedFigure(money.figure(amount), fontSize = 20.sp) }
        compose.onNodeWithText("100,00${nb}DA").assertExists()

        compose.mainClock.autoAdvance = false
        amount = 287_462_331.60
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS / 3L)
        val between = shown()
        assertNotEquals("100,00${nb}DA", between)
        assertNotEquals("287${nb}462${nb}331,60${nb}DA", between)

        compose.mainClock.advanceTimeBy(GLIDE_MILLIS * 2L)
        // To the centime: more digits than an animated Float keeps.
        assertEquals("287${nb}462${nb}331,60${nb}DA", shown())
    }

    @Test
    fun barsMoveFromTheirOldHeightsOrRiseFromZeroOverOtherDays() {
        var heights by mutableStateOf(listOf(0.5f, 1f))
        var days by mutableStateOf("semaine")
        lateinit var drawn: () -> List<Float>
        compose.setContent { drawn = glidingValues(heights, layout = days) }
        compose.waitForIdle()
        assertEquals(listOf(0.5f, 1f), drawn())

        // The same days: each bar from its old height to its new one.
        compose.mainClock.autoAdvance = false
        heights = listOf(1f, 0.5f)
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS / 2L)
        val moving = drawn()
        assertTrue(moving.toString(), moving[0] > 0.5f && moving[0] < 1f && moving[1] > 0.5f && moving[1] < 1f)
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS * 2L)
        assertEquals(listOf(1f, 0.5f), drawn())

        // Other days: every bar rises from the baseline.
        heights = listOf(0.8f, 0.8f, 0.8f)
        days = "mois"
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS / 3L)
        val rising = drawn()
        assertTrue(rising.toString(), rising.size == 3 && rising.all { it > 0f && it < 0.8f })
        compose.mainClock.advanceTimeBy(GLIDE_MILLIS * 2L)
        assertEquals(listOf(0.8f, 0.8f, 0.8f), drawn())
    }

    @Test
    fun aTextBecomingANumberChangesAtOnce() {
        var figure by mutableStateOf(Figure.text("— DA"))
        compose.setContent { AnimatedFigure(figure, fontSize = 20.sp) }
        compose.onNodeWithText("— DA").assertExists()

        compose.mainClock.autoAdvance = false
        figure = money.figure(42.0)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(shown(), shown() == "42,00${nb}DA")
    }
}
