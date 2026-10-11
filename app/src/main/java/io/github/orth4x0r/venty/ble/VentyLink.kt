package io.github.orth4x0r.venty.ble

import kotlinx.coroutines.flow.Flow

/** A live connection to a Venty, real or simulated. */
interface VentyLink : AutoCloseable {
    /** Raw notifications from the device. The flow fails with [LinkLostException] when the link drops. */
    val incoming: Flow<ByteArray>

    /** Sends one request packet. Safe to call from several coroutines; sends are serialised. */
    suspend fun send(packet: ByteArray)
}

class LinkLostException(message: String) : Exception(message)
