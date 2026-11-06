package com.distrigo.app.data.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The account's profile picture: one file per account, kept apart from other accounts, replaced whole. */
class AccountPhotoStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val alice = "6f1c2e7a-0b4d-4c39-9a51-2f3e8d7c6b5a"
    private val bob = "a2b3c4d5-e6f7-4a8b-9c0d-1e2f3a4b5c6d"

    private fun store(dir: File = File(temp.root, "account_photo")) = AccountPhotoStore(dir)

    @Test
    fun `no picture until one is saved`() {
        val store = store()
        assertNull(store.load(alice))
        assertNull(store.saved.value)
    }

    @Test
    fun `a saved picture is found again by a new store, for its account only`() {
        val dir = File(temp.root, "account_photo")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val file = store(dir).save(alice, bytes)!!
        assertArrayEquals(bytes, file.readBytes())

        val again = store(dir)
        assertEquals(file, again.load(alice))
        assertEquals(AccountPhotoStore.Saved(alice, file), again.saved.value)
        // Another account signed in on the same phone sees none
        assertNull(again.load(bob))
        assertNull(again.saved.value)
    }

    @Test
    fun `a new picture replaces the old one under a new name`() {
        val store = store()
        val first = store.save(alice, byteArrayOf(1))!!
        val second = store.save(alice, byteArrayOf(2))!!
        assertNotEquals(first, second)
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(second, store.load(alice))
        assertEquals(second, store.saved.value?.file)
    }

    @Test
    fun `saving the same picture twice keeps one file`() {
        val store = store()
        val first = store.save(alice, byteArrayOf(9, 9))!!
        val second = store.save(alice, byteArrayOf(9, 9))!!
        assertEquals(first, second)
        assertEquals(1, first.parentFile!!.listFiles()!!.size)
    }

    @Test
    fun `removing a picture leaves other accounts' pictures`() {
        val store = store()
        store.save(alice, byteArrayOf(1))
        val bobs = store.save(bob, byteArrayOf(2))!!
        store.load(alice)
        store.delete(alice)
        assertNull(store.saved.value)
        assertNull(store.load(alice))
        assertTrue(bobs.exists())
        assertEquals(bobs, store.load(bob))
    }

    @Test
    fun `removing another account's picture does not clear the one shown`() {
        val store = store()
        val alices = store.save(alice, byteArrayOf(1))!!
        store.delete(bob)
        assertEquals(alices, store.saved.value?.file)
    }

    @Test
    fun `an id that is not a plain id is never made into a path`() {
        val store = store()
        for (bad in listOf("", "../x", "a/b", "a\\b", "é", "a.b", "x".repeat(65))) {
            assertNull(bad, store.save(bad, byteArrayOf(1)))
            assertNull(bad, store.load(bad))
        }
        assertFalse(File(temp.root, "x").exists())
    }

    @Test
    fun `other files in the folder are not taken for a picture`() {
        val dir = File(temp.root, "account_photo").apply { mkdirs() }
        File(dir, "$alice-notahash.jpg").writeBytes(byteArrayOf(1))
        File(dir, "$alice-0123456789abcdef.jpg.tmp").writeBytes(byteArrayOf(1))
        File(dir, "${alice}x-0123456789abcdef.jpg").writeBytes(byteArrayOf(1))
        assertNull(store(dir).load(alice))
    }

    @Test
    fun `an empty picture is refused`() {
        assertNull(store().save(alice, ByteArray(0)))
    }

    @Test
    fun `the folder is made when first needed`() {
        val dir = File(temp.root, "deep/account_photo")
        assertNotNull(store(dir).save(alice, byteArrayOf(5)))
        assertTrue(dir.isDirectory)
    }
}
