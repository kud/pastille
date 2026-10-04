package app.pastille.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderingTest {

    private val categories = listOf(
        CategoryRecord(id = 1, name = "a", position = 0),
        CategoryRecord(id = 2, name = "b", position = 1),
        CategoryRecord(id = 3, name = "c", position = 2),
    )

    @Test
    fun moveRightSwapsWithNeighbour() {
        val moved = moveCategory(categories, id = 1, delta = 1)

        assertEquals(listOf("b", "a", "c"), moved.map { it.name })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test
    fun moveLeftSwapsWithNeighbour() {
        val moved = moveCategory(categories, id = 3, delta = -1)

        assertEquals(listOf("a", "c", "b"), moved.map { it.name })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test
    fun moveRightAtEndIsNoOp() {
        val moved = moveCategory(categories, id = 3, delta = 1)

        assertEquals(listOf("a", "b", "c"), moved.map { it.name })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test
    fun moveLeftAtStartIsNoOp() {
        val moved = moveCategory(categories, id = 1, delta = -1)

        assertEquals(listOf("a", "b", "c"), moved.map { it.name })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test
    fun positionsRenumberedContiguous() {
        val gappy = listOf(
            CategoryRecord(id = 1, name = "a", position = 5),
            CategoryRecord(id = 2, name = "b", position = 10),
            CategoryRecord(id = 3, name = "c", position = 30),
        )

        val moved = moveCategory(gappy, id = 1, delta = 2)

        assertEquals(listOf("b", "c", "a"), moved.map { it.name })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
    }

    @Test
    fun unknownIdKeepsOrderButRenumbers() {
        val gappy = listOf(
            CategoryRecord(id = 1, name = "a", position = 5),
            CategoryRecord(id = 2, name = "b", position = 10),
        )

        val moved = moveCategory(gappy, id = 99, delta = 1)

        assertEquals(listOf("a", "b"), moved.map { it.name })
        assertEquals(listOf(0, 1), moved.map { it.position })
    }

    @Test
    fun `a dragged order is numbered from zero`() {
        val folders = listOf(
            CategoryRecord(id = 1, name = "Work", position = 0),
            CategoryRecord(id = 2, name = "Home", position = 1),
            CategoryRecord(id = 3, name = "Travel", position = 2),
        )
        assertEquals(
            listOf(3L to 0, 1L to 1, 2L to 2),
            orderCategories(folders, listOf(3, 1)).map { it.id to it.position },
        )
    }
}
