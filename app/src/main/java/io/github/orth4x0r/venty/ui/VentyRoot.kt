package io.github.orth4x0r.venty.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.ui.connect.ConnectScreen
import io.github.orth4x0r.venty.ui.connect.PermissionScreen
import io.github.orth4x0r.venty.ui.dashboard.DashboardScreen
import io.github.orth4x0r.venty.ui.theme.VentyColors

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun VentyRoot(controller: VentyController) {
    val context = LocalContext.current
    fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasPermissions()) }
    var bluetoothOn by remember { mutableStateOf(controller.bluetoothEnabled) }
    var triedAutoConnect by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasPermissions() }
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothOn = controller.bluetoothEnabled }

    // Permissions and Bluetooth can change while we're in the background.
    LifecycleResumeEffect(Unit) {
        granted = hasPermissions()
        bluetoothOn = controller.bluetoothEnabled
        onPauseOrDispose { }
    }

    val connection by controller.connection.collectAsStateWithLifecycle()

    LaunchedEffect(granted, bluetoothOn) {
        if (granted && bluetoothOn && !triedAutoConnect && connection == ConnectionState.Idle) {
            triedAutoConnect = true
            controller.connectToLast()
        }
    }

    val screen = when {
        connection is ConnectionState.Connected ||
            connection is ConnectionState.Connecting ||
            connection is ConnectionState.Reconnecting -> Screen.Dashboard
        !granted -> Screen.Permissions
        else -> Screen.Connect
    }

    Box(Modifier.fillMaxSize().background(VentyColors.Background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Permissions -> PermissionScreen(
                    onGrant = { permissionLauncher.launch(requiredPermissions) },
                    onDemo = controller::startDemo,
                )
                Screen.Connect -> ConnectScreen(
                    connection = connection,
                    bluetoothOn = bluetoothOn,
                    lastDeviceName = controller.lastDeviceName,
                    onEnableBluetooth = {
                        enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onScan = controller::startScan,
                    onStopScan = controller::stopScan,
                    onConnect = controller::connect,
                    onReconnectLast = { controller.connectToLast() },
                    onDemo = controller::startDemo,
                )
                Screen.Dashboard -> DashboardScreen(
                    controller = controller,
                    connection = connection,
                )
            }
        }
    }
}

private enum class Screen { Permissions, Connect, Dashboard }
