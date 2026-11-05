package io.github.orth4x0r.venty.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer APK, downloads it straight into a [PackageInstaller] session
 * and hands it to Android's own install prompt. No Play Store, no extra services.
 *
 * The update only installs over the current app when both are signed with the same key, which
 * holds for builds from the GitHub releases page.
 */
object AppUpdater {
    sealed interface State {
        /** Nothing known yet. */
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAtMs: Long) : State
        data class Available(val update: UpdateInfo, val hint: String? = null) : State
        /** [progress] is 0..1, or null when GitHub didn't send a size. */
        data class Downloading(val update: UpdateInfo, val progress: Float?) : State
        /** Downloaded; waiting for the person to confirm Android's install prompt. */
        data class Confirm(val update: UpdateInfo, val intent: Intent) : State
        data class Failed(val message: String, val update: UpdateInfo? = null) : State
    }

    private const val REPO = "orth4x0r/venty-android"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val AUTO_CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
    private const val PREFS = "updates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var restored = false

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /** Shows the last check's result straight away, then checks again if it's been a while. */
    fun maybeAutoCheck(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        restore(app)
        if (!enabled || job?.isActive == true) return
        val last = prefs(app).getLong("checked_at", 0L)
        if (System.currentTimeMillis() - last >= AUTO_CHECK_EVERY_MS) check(app, quiet = true)
    }

    /** Asks GitHub for the latest release. [quiet] keeps a failed background check from showing an error. */
    fun check(context: Context, quiet: Boolean = false) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        val before = _state.value
        _state.value = State.Checking
        job = scope.launch {
            _state.value = try {
                val update = fetchLatest(currentVersion(app))
                val now = System.currentTimeMillis()
                prefs(app).edit()
                    .putLong("checked_at", now)
                    .putString("available", update?.let(::toJson)?.toString())
                    .apply()
                if (update != null) State.Available(update) else State.UpToDate(now)
            } catch (e: IOException) {
                if (quiet) before.takeUnless { it is State.Checking } ?: State.Idle
                else State.Failed("Couldn't reach GitHub. Check your connection and try again.")
            } catch (e: Exception) {
                if (quiet) before.takeUnless { it is State.Checking } ?: State.Idle
                else State.Failed("GitHub sent something unexpected (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    /** Downloads [update] and opens Android's install prompt. */
    fun install(context: Context, update: UpdateInfo) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        // Android 8+ asks once per app before it may install others ("Install unknown apps").
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            _state.value = State.Available(update, hint = "Allow installs from this app, then come back and tap again")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return
        }
        _state.value = State.Downloading(update, if (update.apkSize > 0) 0f else null)
        job = scope.launch {
            val installer = app.packageManager.packageInstaller
            var sessionId = -1
            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (update.apkSize > 0) setSize(update.apkSize)
                    // Lets later updates skip the prompt once Android sees this app as the installer.
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = open(update.apkUrl, accept = "application/octet-stream")
                    try {
                        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.apkSize
                        session.openWrite("base.apk", 0, total.takeIf { it > 0 } ?: -1).use { out ->
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                var lastShown = -1
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastShown) {
                                            lastShown = pct
                                            _state.value = State.Downloading(update, (done.toFloat() / total).coerceIn(0f, 1f))
                                        }
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    val callback = PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        Intent(app, UpdateInstallReceiver::class.java).putExtra(EXTRA_VERSION, update.version),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
                // The receiver takes it from here.
            } catch (e: Exception) {
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                _state.value = if (e is kotlinx.coroutines.CancellationException) {
                    State.Available(update)
                } else {
                    State.Failed("Download failed (${e.message ?: e.javaClass.simpleName}). Try again.", update)
                }
            }
        }
    }

    /** Called by [UpdateInstallReceiver] with Android's answer to the install session. */
    internal fun onInstallStatus(context: Context, status: Int, message: String?, confirm: Intent?) {
        val update = when (val s = _state.value) {
            is State.Downloading -> s.update
            is State.Confirm -> s.update
            is State.Available -> s.update
            is State.Failed -> s.update
            else -> null
        } ?: restoredUpdate(context.applicationContext)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirm == null || update == null) return
                _state.value = State.Confirm(update, confirm)
                // Works while the app is open; otherwise the Install button in Settings does it.
                runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED -> if (update != null) _state.value = State.Available(update)
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> _state.value = State.Failed(
                "Android won't install it over this copy, which was probably signed with a different key. " +
                    "Uninstall the app and install the new APK from GitHub.",
                update,
            )
            PackageInstaller.STATUS_FAILURE_STORAGE -> _state.value = State.Failed("Not enough storage to install the update.", update)
            else -> _state.value = State.Failed("Install failed${message?.let { ": $it" } ?: ""}.", update)
        }
    }

    /** Re-opens Android's install prompt if it was dismissed or couldn't show. */
    fun confirm(context: Context, intent: Intent) {
        runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { _state.value = State.Failed("Couldn't open the installer (${it.message}).") }
    }

    internal const val EXTRA_VERSION = "io.github.orth4x0r.venty.UPDATE_VERSION"

    private fun fetchLatest(current: String): UpdateInfo? {
        val conn = open(LATEST_URL, accept = "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                404 -> return null // No releases yet.
                in 200..299 -> Unit
                403, 429 -> throw IOException("GitHub is rate-limiting this network; try again later")
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.getString("tag_name")
            if (!UpdateVersion.isNewer(tag, current)) return null
            val assets = json.optJSONArray("assets") ?: return null
            val names = List(assets.length()) { assets.getJSONObject(it).optString("name") }
            val i = UpdateVersion.pickPhoneApk(names)
            if (i < 0) return null
            val asset = assets.getJSONObject(i)
            return UpdateInfo(
                version = tag.trim().removePrefix("v").removePrefix("V"),
                apkUrl = asset.getString("browser_download_url"),
                apkSize = asset.optLong("size", 0L),
                notes = UpdateVersion.plainNotes(json.optString("body")),
                pageUrl = json.optString("html_url", "https://github.com/$REPO/releases/latest"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // Asset downloads redirect to GitHub's CDN.
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "venty-android")
        }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Brings back a found-but-not-installed update after the app restarts, so the dot stays. */
    private fun restore(context: Context) {
        if (restored) return
        restored = true
        if (_state.value != State.Idle) return
        val update = restoredUpdate(context) ?: return
        // Drop it if this build already caught up (installed by hand, say).
        if (UpdateVersion.isNewer(update.version, currentVersion(context))) {
            _state.value = State.Available(update)
        } else {
            prefs(context).edit().remove("available").apply()
        }
    }

    private fun restoredUpdate(context: Context): UpdateInfo? =
        prefs(context).getString("available", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

    private fun toJson(u: UpdateInfo) = JSONObject()
        .put("version", u.version)
        .put("apk", u.apkUrl)
        .put("size", u.apkSize)
        .put("notes", u.notes)
        .put("page", u.pageUrl)

    private fun fromJson(o: JSONObject) = UpdateInfo(
        version = o.getString("version"),
        apkUrl = o.getString("apk"),
        apkSize = o.optLong("size", 0L),
        notes = o.optString("notes"),
        pageUrl = o.optString("page"),
    )
}
