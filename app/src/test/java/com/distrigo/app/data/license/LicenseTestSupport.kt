package com.distrigo.app.data.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val SECOND = 1_000L
internal const val MINUTE = 60 * SECOND
internal const val HOUR = 60 * MINUTE
internal const val DAY = 24 * HOUR

/**
 * The golden tokens: signed by tools/license/golden-tokens.mjs with Node's crypto, the way the server signs,
 * so the app is tested against tokens it did not make. Regenerate with `node tools/license/golden-tokens.mjs`.
 */
internal object Golden {

    private val json: JsonObject by lazy {
        val stream = Golden::class.java.getResourceAsStream("/license/golden.json") ?: error("golden.json not on the classpath")
        JsonParser().parse(stream.reader().readText()).asJsonObject
    }

    private val claims get() = json.getAsJsonObject("claims")
    private val lateClaims get() = json.getAsJsonObject("late_claims")

    const val PACKAGE = "com.distrigo.app"

    val trustedKeys: Map<String, String> by lazy {
        json.getAsJsonObject("trusted").entrySet().associate { (kid, key) -> kid to key.asString }
    }
    val trusted: Map<String, PublicKey> by lazy { trustedKeys.mapValues { (_, key) -> LicenseCrypto.publicKey(key) } }

    val deviceHash: String get() = json.getAsJsonObject("device").get("hash").asString
    val deviceSpki: ByteArray get() = Base64.getDecoder().decode(json.getAsJsonObject("device").get("spki").asString)
    val installationId: String get() = claims.get("iid").asString

    val tokenNames: Set<String> get() = json.getAsJsonObject("tokens").keySet()
    fun token(name: String): String = json.getAsJsonObject("tokens").get(name)?.asString ?: error("no golden token $name")

    private fun JsonObject.millis(name: String) = get(name).asLong * 1000

    val validFrom get() = claims.millis("vf")
    val validTo get() = claims.millis("vt")
    val graceUntil get() = claims.millis("gu")
    val issuedAt get() = claims.millis("iat")
    val offlineUntil get() = claims.millis("ou")
    val lateIssuedAt get() = lateClaims.millis("iat")
    val lateOfflineUntil get() = lateClaims.millis("ou")

    fun binding(minSeq: Long = 0) = LicenseBinding(PACKAGE, installationId, deviceHash, minSeq)
}

/**
 * A phone whose real time the test controls, with what its user can change (the date, automatic time, power)
 * and what they cannot (the monotonic clock, the boot count).
 */
internal class FakePhone(start: Long) : Clocks {

    /** The real time. Nothing on the phone can change it. */
    var real = start
        private set

    /** Booted an hour before the test starts. */
    private var bootedAt = start - HOUR
    private var boots = 41
    private var wallOffset = 0L
    private var automatic = true

    var bootCountKnown = true
    var networkTimeAvailable = false

    /** Time passes, the phone awake or asleep: the monotonic clock counts both. */
    fun pass(duration: Long) {
        require(duration >= 0)
        real += duration
    }

    fun passTo(time: Long) = pass(time - real)

    /** Switched off for [duration], then on again: a reboot. The date keeps running meanwhile. */
    fun switchOff(duration: Long) {
        real += duration
        boots++
        bootedAt = real
    }

    /** The user sets the date by hand, which turns automatic time off. */
    fun setDate(time: Long) {
        automatic = false
        wallOffset = time - real
    }

    /** Automatic time back on, with a network to take it from. */
    fun automaticDate() {
        automatic = true
        wallOffset = 0
    }

    /** The network itself sends a wrong time (a carrier's NITZ), with automatic time on. */
    fun networkGlitch(ahead: Long) {
        automatic = true
        wallOffset = ahead
    }

    override fun elapsedRealtime() = real - bootedAt
    override fun bootCount() = if (bootCountKnown) boots else null
    override fun wall() = real + wallOffset
    override fun autoTime() = automatic
    override fun networkTime() = if (networkTimeAvailable) real else null
}

/** An anchor kept in memory: for tests of the clock alone, which read it thousands of times. */
internal class MemoryAnchorStore : AnchorStore {
    private var state: AnchorState? = null
    override fun load() = state
    override fun save(state: AnchorState) {
        this.state = state
    }
}

/** The Keystore HMAC, in software: what the phone's [KeystoreSeal] does, with a key the test chooses. */
internal class HmacSeal(seed: Int = 1) : StateSeal {
    private val key = SecretKeySpec(ByteArray(32) { (it * 31 + seed).toByte() }, "HmacSHA256")

    override fun tag(data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(data)
    }
}
