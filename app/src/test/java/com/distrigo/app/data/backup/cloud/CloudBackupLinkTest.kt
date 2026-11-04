package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.auth.SoftwareSessionCipher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** Linking: a new key with its recovery code, or an existing one opened with the password or the code. */
class CloudBackupLinkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val cloud = FakeCloudBackupProvider()
    private val store by lazy { CloudBackupStore(File(folder.root, "state")) }

    private fun keysOnPhone(name: String = "keys") = BackupKeyStore(File(folder.root, name), SoftwareSessionCipher())

    private fun link(keys: BackupKeyStore = keysOnPhone(), state: CloudBackupStore = store) = CloudBackupLink(
        state, keys, { cloud }, clock = { Instant.parse("2026-11-03T10:00:00Z") }, passwordIterations = 1_000, recoveryIterations = 100,
    )

    @Test
    fun newKeyGoesToTheAccountAndThePhone() = runBlocking {
        val keys = keysOnPhone()
        assertNull(link(keys).existingRing("owner@gmail.com", "biz-1"))
        val code = link(keys).createKey("owner@gmail.com", "biz-1", "Tournee2026!")

        val ring = link(keys).existingRing("owner@gmail.com", "biz-1")!!
        val key = keys.loadKey("biz-1")!!
        assertTrue(ring.unlockWithPassword("Tournee2026!")!!.sameAs(key))
        assertTrue(ring.unlockWithRecoveryCode(code)!!.sameAs(key))
        assertEquals(ring.toJson(), keys.loadKeyRing("biz-1")!!.toJson())

        val state = store.read()
        assertTrue(state.linked)
        assertEquals("owner@gmail.com", state.account)
        assertEquals("biz-1", state.businessId)
        assertEquals("fake", state.providerId)
    }

    @Test
    fun anotherPhoneJoinsWithThePasswordOrTheCode() = runBlocking {
        val code = link().createKey("owner@gmail.com", "biz-1", "Tournee2026!")
        val first = keysOnPhone().loadKey("biz-1")!!

        val secondPhone = keysOnPhone("second")
        val secondState = CloudBackupStore(File(folder.root, "second-state"))
        val ring = link(secondPhone, secondState).existingRing("owner@gmail.com", "biz-1")!!
        assertFalse(link(secondPhone, secondState).join("owner@gmail.com", ring, "wrong password"))
        assertFalse(secondState.read().linked)
        assertNull(secondPhone.loadKey("biz-1"))

        assertTrue(link(secondPhone, secondState).join("owner@gmail.com", ring, "Tournee2026!"))
        assertTrue(secondPhone.loadKey("biz-1")!!.sameAs(first))

        val thirdPhone = keysOnPhone("third")
        assertTrue(link(thirdPhone, CloudBackupStore(File(folder.root, "third-state"))).join("owner@gmail.com", ring, code.lowercase()))
        assertTrue(thirdPhone.loadKey("biz-1")!!.sameAs(first))
    }

    @Test
    fun changedPasswordReachesTheAccount() = runBlocking {
        val keys = keysOnPhone()
        val code = link(keys).createKey("owner@gmail.com", "biz-1", "Tournee2026!")
        link(keys).changePassword("Nouveau-mot-2027")
        val ring = link(keys).existingRing("owner@gmail.com", "biz-1")!!
        assertNull(ring.unlockWithPassword("Tournee2026!"))
        assertNotNull(ring.unlockWithPassword("Nouveau-mot-2027"))
        assertNotNull(ring.unlockWithRecoveryCode(code))
        assertEquals(ring.toJson(), keys.loadKeyRing("biz-1")!!.toJson())
    }

    @Test
    fun forgettingKeepsTheBackupsAndTheSettings() = runBlocking {
        val keys = keysOnPhone()
        link(keys).createKey("owner@gmail.com", "biz-1", "Tournee2026!")
        store.update { it.copy(allowMobileData = true, includePhotos = false, lastEntryId = "f1") }

        link(keys).forget()

        val state = store.read()
        assertFalse(state.linked)
        assertNull(state.account)
        assertNull(state.lastEntryId)
        assertTrue(state.allowMobileData)
        assertFalse(state.includePhotos)
        assertNull(keys.loadKey("biz-1"))
        assertNotNull("the ring stays in the account", cloud.readKeyRing("biz-1"))
    }

    @Test
    fun relinkingStartsAFreshRecord() = runBlocking {
        store.update { it.copy(lastEntryId = "old", lastFingerprint = "fp", problemStreak = 3, lastProblem = CloudBackupProblem.NEEDS_CONSENT) }
        link().createKey("owner@gmail.com", "biz-1", "Tournee2026!")
        val state = store.read()
        assertNull(state.lastEntryId)
        assertNull(state.lastFingerprint)
        assertEquals(0, state.problemStreak)
        assertNull(state.lastProblem)
    }
}
