package io.github.orth4x0r.venty.companion

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import java.util.concurrent.Executor

private const val TAG = "CompanionLink"

/**
 * "Connect when it turns on": the Venty is associated with the app as a companion device, and
 * Android then tells [VentyCompanionService] whenever it starts advertising, even when the app
 * has been closed. That's the only way to be woken by the device, since the phone can't wake it.
 *
 * Presence observing needs Android 12 (API 31). Association itself is a system dialog; no
 * bonding or pairing happens.
 */
object CompanionLink {
    private const val PREFS = "companion"
    private const val KEY_ADDRESS = "address"
    private const val KEY_ID = "association_id"

    /** Android 12+ and a phone that offers companion device setup. */
    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature("android.software.companion_device_setup")

    /** The address currently associated and observed, or null. */
    fun observedAddress(context: Context): String? =
        prefs(context).getString(KEY_ADDRESS, null)?.takeIf { isAssociated(context, it) }

    /**
     * Asks Android to associate [address]. Android answers with a confirmation dialog, which the
     * caller launches from [onConfirm]; after the user accepts, call [onAssociated].
     */
    fun associate(context: Context, address: String, onConfirm: (IntentSender) -> Unit, onError: (String) -> Unit) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return onError("Not supported on this phone")
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setDeviceAddress(address).build())
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            // API 33+
            override fun onAssociationPending(intentSender: IntentSender) = onConfirm(intentSender)

            // API 32 and below
            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(intentSender: IntentSender) = onConfirm(intentSender)

            // API 33+: the id is what Android 16 wants for presence observing.
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                prefs(context).edit().putInt(KEY_ID, associationInfo.id).apply()
            }

            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "Couldn't set up auto-connect")
        }
        prefs(context).edit().putString(KEY_ADDRESS, address).remove(KEY_ID).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.associate(request, MainExecutor(context), callback)
        } else {
            @Suppress("DEPRECATION")
            cdm.associate(request, callback, null)
        }
    }

    /** The user accepted the dialog: start watching for the device. */
    @SuppressLint("MissingPermission") // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE is a normal permission.
    fun onAssociated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val address = prefs(context).getString(KEY_ADDRESS, null) ?: return
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching {
            val id = associationId(context, address)
            if (Build.VERSION.SDK_INT >= 36 && id != null) {
                cdm.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(id).build())
            } else {
                @Suppress("DEPRECATION")
                cdm.startObservingDevicePresence(address)
            }
        }.onFailure { Log.w(TAG, "couldn't observe $address", it) }
    }

    /** Stops watching and removes the association. */
    @SuppressLint("MissingPermission")
    fun disassociate(context: Context) {
        val address = prefs(context).getString(KEY_ADDRESS, null)
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm != null && address != null) {
            runCatching {
                val id = associationId(context, address)
                if (Build.VERSION.SDK_INT >= 36 && id != null) {
                    cdm.stopObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(id).build())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    @Suppress("DEPRECATION")
                    cdm.stopObservingDevicePresence(address)
                }
            }
            runCatching {
                val id = associationId(context, address)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && id != null) {
                    cdm.disassociate(id)
                } else {
                    @Suppress("DEPRECATION")
                    cdm.disassociate(address)
                }
            }.onFailure { Log.w(TAG, "disassociate failed", it) }
        }
        prefs(context).edit().clear().apply()
    }

    private fun isAssociated(context: Context, address: String): Boolean {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { it.equals(address, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    private fun associationId(context: Context, address: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        prefs(context).getInt(KEY_ID, -1).takeIf { it >= 0 }?.let { return it }
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return null
        return cdm.myAssociations.firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }?.id
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private class MainExecutor(context: Context) : Executor {
        private val handler = android.os.Handler(context.mainLooper)
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }
}
