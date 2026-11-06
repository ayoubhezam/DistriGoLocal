package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Several photos picked at once: what goes in, what the gallery already has, what does not fit under five. */
class ImageAddPlanTest {

    private val max = 5

    @Test
    fun `photos picked into an empty gallery all go in, in the order picked`() {
        assertEquals(ImageAddPlan(listOf("a", "b", "c"), 0, 0), planImageAdds(emptyList(), listOf("a", "b", "c"), max))
    }

    @Test
    fun `only the room left is filled, the rest is counted`() {
        assertEquals(ImageAddPlan(listOf("d", "e"), 0, 2), planImageAdds(listOf("a", "b", "c"), listOf("d", "e", "f", "g"), max))
    }

    @Test
    fun `a photo the gallery has, or picked twice, is not added again`() {
        assertEquals(ImageAddPlan(listOf("c"), 2, 0), planImageAdds(listOf("a", "b"), listOf("a", "c", "c"), max))
    }

    @Test
    fun `a full gallery takes nothing, and says which were already there`() {
        val full = listOf("a", "b", "c", "d", "e")
        assertEquals(ImageAddPlan(emptyList(), 1, 1), planImageAdds(full, listOf("a", "z"), max))
    }

    @Test
    fun `a duplicate does not take the place of a photo that fits`() {
        // Four already: "a" again is a duplicate, "x" takes the last place, "y" is over
        assertEquals(ImageAddPlan(listOf("x"), 1, 1), planImageAdds(listOf("a", "b", "c", "d"), listOf("a", "x", "y"), max))
    }

    @Test
    fun `every photo picked is accounted for exactly once`() {
        val r = kotlin.random.Random(3)
        repeat(5_000) {
            val existing = List(r.nextInt(0, 6)) { "e$it" }
            val picked = List(r.nextInt(0, 8)) { if (r.nextBoolean()) "e${r.nextInt(0, 6)}" else "p${r.nextInt(0, 6)}" }
            val plan = planImageAdds(existing, picked, max)
            assertEquals(picked.size, plan.toAdd.size + plan.duplicates + plan.overLimit)
            assertEquals(true, existing.size + plan.toAdd.size <= maxOf(max, existing.size))
            assertEquals(plan.toAdd.size, plan.toAdd.toSet().size)
            assertEquals(true, plan.toAdd.none { it in existing })
        }
    }
}
