package io.github.orth4x0r.venty.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.util.Log
import io.github.orth4x0r.venty.ble.VentyProtocol
import io.github.orth4x0r.venty.ble.VolcanoProtocol
import java.util.concurrent.Executor
import java.util.regex.Pattern

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
     * Asks Android to associate the device called [name] at [address]. Android answers with a
     * dialog that scans for it, which the caller launches from [onConfirm]; after the user
     * accepts, call [onAssociated]. The device must be advertising, so drop any open link first.
     *
     * The dialog matches on the advertised name (`S&B VY` plus the serial, so it's unique) rather
     * than on the address: an address `ScanFilter` is treated as a public address on Android 13+,
     * and some phones' hardware filters then never match a device with a random address.
     */
    fun associate(
        context: Context,
        address: String,
        name: String?,
        onConfirm: (IntentSender) -> Unit,
        onError: (String) -> Unit,
    ) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return onError("Not supported on this phone")
        // Only a real advertised name narrows it to this one device; a placeholder like "Venty"
        // falls back to any Storz & Bickel device, and the user picks theirs in the dialog.
        val advertised = name?.trim()?.takeIf { VentyProtocol.isVentyName(it) || VolcanoProtocol.isVolcanoName(it) }
        val pattern = if (advertised != null) {
            Pattern.compile(".*" + Pattern.quote(advertised) + ".*")
        } else {
            Pattern.compile("(?i).*(S&B VY|S&B VZ|VOLCANO).*")
        }
        val filter = BluetoothLeDeviceFilter.Builder()
            .setNamePattern(pattern)
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(advertised != null)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            // API 33+
            override fun onAssociationPending(intentSender: IntentSender) = onConfirm(intentSender)

            // API 32 and below
            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(intentSender: IntentSender) = onConfirm(intentSender)

            // API 33+: the id is what Android 16 wants for presence observing.
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val edit = prefs(context).edit().putInt(KEY_ID, associationInfo.id)
                associationInfo.deviceMacAddress?.toString()?.uppercase()?.let { edit.putString(KEY_ADDRESS, it) }
                edit.apply()
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

    /**
     * The user accepted the dialog: start watching for the device. [result] is the dialog's
     * result intent; before Android 13 it's the only place that says which device was picked.
     */
    @SuppressLint("MissingPermission") // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE is a normal permission.
    fun onAssociated(context: Context, result: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        pickedAddress(result)?.let { prefs(context).edit().putString(KEY_ADDRESS, it).apply() }
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

    private fun pickedAddress(result: Intent?): String? {
        if (result == null) return null
        @Suppress("DEPRECATION")
        val picked: Any? = result.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        return when (picked) {
            is ScanResult -> picked.device.address
            is BluetoothDevice -> picked.address
            else -> null
        }?.uppercase()
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
