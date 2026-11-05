package io.github.orth4x0r.venty.update

/** A newer build published on GitHub Releases. */
data class UpdateInfo(
    /** The version without the leading "v", e.g. "0.3.2". */
    val version: String,
    val apkUrl: String,
    /** The APK's size in bytes, or 0 when GitHub didn't say. */
    val apkSize: Long,
    /** The release notes, flattened to plain text. */
    val notes: String,
    /** The release page, for "See on GitHub". */
    val pageUrl: String,
)

/** Version and asset rules for the update check, kept free of Android so they can be unit-tested. */
object UpdateVersion {
    /** "v0.3.1", "0.3.1" or "0.3.1-beta" to [0, 3, 1]; null when it isn't a version. */
    fun parse(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** True when [remote] is a higher version than [current]. Unparseable versions never count as newer. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return true
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * The phone APK among a release's asset names: an .apk that isn't the Wear OS build, preferring
     * one named venty-*. Returns its index, or -1.
     */
    fun pickPhoneApk(names: List<String>): Int {
        val apks = names.withIndex().filter { (_, n) ->
            val l = n.lowercase()
            l.endsWith(".apk") && "wear" !in l
        }
        return (apks.firstOrNull { it.value.lowercase().startsWith("venty") } ?: apks.firstOrNull())?.index ?: -1
    }

    /** Markdown release notes as plain text: no headings, emphasis, code ticks or link targets. */
    fun plainNotes(markdown: String): String = markdown
        .replace("\r\n", "\n")
        .lines()
        .map { line ->
            line.trimEnd()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*+]\\s+"), "• ")
                .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
