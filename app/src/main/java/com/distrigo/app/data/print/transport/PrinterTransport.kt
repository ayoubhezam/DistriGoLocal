package com.distrigo.app.data.print.transport

/**
 * Why a print did not happen, in terms the user can act on.
 *
 * Deliberately not an exception message. A rep standing in front of a client cannot read
 * "java.io.IOException: read failed, socket might closed or timeout, read ret: -1" — which is what a
 * cheap printer out of range actually throws — so every failure is mapped to one of these and every
 * one of them has a button beside it in the UI.
 */
enum class PrintFailure {
    /** The phone has no Bluetooth radio at all. */
    NO_BLUETOOTH,

    /** BLUETOOTH_CONNECT / BLUETOOTH_SCAN not granted. */
    PERMISSION_DENIED,

    /** The radio is off. */
    ADAPTER_OFF,

    /** Saved, but not paired with this phone — or the pairing was removed in Android's settings. */
    NOT_PAIRED,

    /** Paired but not answering: out of range, switched off, or already connected to another phone. */
    UNREACHABLE,

    /** The connection opened and then broke mid-job, so the paper holds half a receipt. */
    INTERRUPTED,

    /** A network printer, and this phone is on no network at all. */
    NO_NETWORK,

    /** Nothing is configured to print to. */
    NOT_CONFIGURED,
}

/** Thrown by a transport so the caller gets a [PrintFailure] rather than a stack trace to interpret. */
class PrintException(val failure: PrintFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)

/**
 * How a transport paces itself, stated so a caller can tell its own delay from the link's speed.
 *
 * The pauses exist because these printers apply no back-pressure: hand one a whole receipt at once
 * and the overflow is silent, leaving a band of the middle missing. At text sizes the cost is
 * invisible; at raster sizes it is not, and the two failure modes — "the link is slow" and "our
 * pacing is too cautious for a payload this size" — have opposite fixes.
 */
data class Pacing(val chunkBytes: Int, val pauseMs: Long, val drainMs: Long) {
    /** What sending [totalBytes] will spend asleep rather than transmitting. */
    fun overheadMs(totalBytes: Int): Long =
        ((totalBytes - 1).coerceAtLeast(0) / chunkBytes).toLong() * pauseMs + drainMs
}

/**
 * A one-way pipe to a printer.
 *
 * **Open per job, not per session.** Connect, write, close, every time. Holding an RFCOMM socket open
 * between receipts is faster and is what a counter-bound printer would want, but a belt printer goes
 * in and out of range all day, and a socket that has silently died fails on the *next* receipt rather
 * than on a connect the user is already watching. Reconnecting costs about a second and makes every
 * failure land where the user can see it.
 */
interface PrinterTransport {

    /** This transport's own chunking, for callers that need to account for it. */
    val pacing: Pacing

    /**
     * Sends [bytes] to the printer at [address], throwing [PrintException] if it cannot.
     *
     * Suspending, and expected to do its blocking work off the caller's thread.
     */
    suspend fun send(address: String, bytes: ByteArray)

    /** Whether the printer answers right now, without printing anything. */
    suspend fun isReachable(address: String): Boolean
}
