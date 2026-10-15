package com.distrigo.app.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The printable cards' grid (docs/activation_codes.md, step C4): ten to an A4 page, all inside it. */
class CodeCardsPdfTest {

    @Test
    fun `ten cards to a page`() {
        assertEquals(1, CodeCardsPdf.pages(1))
        assertEquals(1, CodeCardsPdf.pages(10))
        assertEquals(2, CodeCardsPdf.pages(11))
        assertEquals(10, CodeCardsPdf.pages(100))
    }

    @Test
    fun `every card sits inside the page, a little larger than a bank card, and none overlap`() {
        // A4, in points
        val (pageW, pageH) = 595f to 842f
        // A bank card is 85.6 × 54 mm; a point is 0.3528 mm
        assertTrue(CodeCardsPdf.CARD_W * 0.3528f in 85f..100f)
        assertTrue(CodeCardsPdf.CARD_H * 0.3528f in 48f..56f)
        val boxes = (0 until CodeCardsPdf.PER_PAGE).map { CodeCardsPdf.cardOrigin(it) }
        for ((left, top) in boxes) {
            assertTrue(left >= 0f && left + CodeCardsPdf.CARD_W <= pageW)
            // The page's footer line lives below the last row
            assertTrue(top >= 0f && top + CodeCardsPdf.CARD_H <= pageH - 20f)
        }
        assertEquals(CodeCardsPdf.PER_PAGE, boxes.toSet().size)
        assertEquals(boxes[0], CodeCardsPdf.cardOrigin(CodeCardsPdf.PER_PAGE))
    }
}
