package com.nuvio.tv.core.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

data class SubtitleInput(
    val url: String,
    val name: String,
    val lang: String
)

/**
 * Input data for launching an external video player.
 */
data class ExternalPlayerInput(
    val url: String,
    val title: String? = null,
    val headers: Map<String, String>? = null,
    val resumePositionMs: Long = 0L,
    val subtitles: List<SubtitleInput>? = null,
    val selectedSubtitleIndex: Int = -1,
    val preferredLanguages: List<String> = emptyList()
)

/**
 * Result returned by an external video player after playback ends.
 * Not all players return this data — MX Player, VLC, and Just Player are known to support it.
 */
data class ExternalPlayerResult(
    val positionMs: Long,
    val durationMs: Long?,
    val endedByUser: Boolean = true
)

/**
 * ActivityResultContract that launches an external video player via ACTION_VIEW
 * and parses the playback position returned by the player (if supported).
 *
 * Supported players:
 * - MX Player: returns "position" (Int, ms), "duration" (Int, ms), "end_by" (String)
 * - VLC: returns "extra_position" (Long, ms), "extra_duration" (Long, ms)
 * - Just Player: returns "position" (Int, ms), "duration" (Int, ms)
 * - mpv-android: returns "position" (Int, ms), "duration" (Int, ms)
 */
class ExternalPlayerResultContract : ActivityResultContract<ExternalPlayerInput, ExternalPlayerResult?>() {

    override fun createIntent(context: Context, input: ExternalPlayerInput): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(input.url), "video/*")

            input.title?.let {
                putExtra("title", it)
                putExtra(Intent.EXTRA_TITLE, it)
            }

            input.headers?.let { hdrs ->
                if (hdrs.isNotEmpty()) {
                    val headerArray = hdrs.entries.map { "${it.key}: ${it.value}" }.toTypedArray()
                    putExtra("headers", headerArray)
                }
            }

            // Resume position — supported by MX Player, VLC, Just Player, mpv-android
            if (input.resumePositionMs > 0L) {
                putExtra("position", input.resumePositionMs.toInt())  // MX Player / Just Player / mpv (Int ms)
                putExtra("from_start", false)                         // VLC: don't force start from beginning
            }

            // Request that the player returns result with position/duration.
            // Required by MX Player; harmless for other players.
            putExtra("return_result", true)

            // Inject subtitle extras for external players
            val subs = input.subtitles
            if (!subs.isNullOrEmpty()) {
                val subtitleUris = subs.map { Uri.parse(it.url) }.toTypedArray()
                val subtitleNames = subs.map { it.name }.toTypedArray()
                val subtitleFilenames = subs.map { "${it.lang}_${it.name}.srt" }.toTypedArray()

                // 1. MX Player / Nova / mpv-android
                putExtra("subs", subtitleUris)
                putExtra("subs.name", subtitleNames)
                putExtra("subs.filename", subtitleFilenames)

                val enabledIndex = if (input.selectedSubtitleIndex in subs.indices) {
                    input.selectedSubtitleIndex
                } else {
                    var foundIndex = -1
                    for (target in input.preferredLanguages) {
                        val idx = subs.indexOfFirst { matchesLanguage(it.lang, target) }
                        if (idx >= 0) {
                            foundIndex = idx
                            break
                        }
                    }
                    if (foundIndex >= 0) foundIndex else 0
                }

                if (enabledIndex in subs.indices) {
                    putExtra("subs.enable", arrayOf(Uri.parse(subs[enabledIndex].url)))
                }

                // 2. Just Player (ExoPlayer arrays and active track)
                val activeSub = subs.getOrNull(enabledIndex)
                if (activeSub != null) {
                    putExtra("subtitle", Uri.parse(activeSub.url))
                    putExtra("subtitle_uri", Uri.parse(activeSub.url))
                    putExtra("subtitle_name", activeSub.name)
                }
                putExtra("subtitle_uri", subtitleUris)
                putExtra("subtitle_name", subtitleNames)

                // 3. VLC
                if (activeSub != null) {
                    putExtra("subtitles", activeSub.url)
                    putExtra("subtitles", Uri.parse(activeSub.url))
                }
            }

            // Do NOT add FLAG_ACTIVITY_NEW_TASK — it prevents receiving the result.
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ExternalPlayerResult? {
        // Some players return RESULT_OK, others return RESULT_CANCELED even on normal exit.
        // We try to parse position regardless of resultCode.
        android.util.Log.d("ExtPlayerContract", "parseResult: resultCode=$resultCode, intent=$intent, extras=${intent?.extras?.keySet()?.toList()}")
        val data = intent ?: return null

        val position = parsePosition(data) ?: return null
        val duration = parseDuration(data)
        val endedByUser = parseEndReason(data)

        return ExternalPlayerResult(
            positionMs = position,
            durationMs = duration,
            endedByUser = endedByUser
        )
    }

    private fun parsePosition(data: Intent): Long? {
        // VLC uses Long extras
        val vlcPosition = data.getLongExtra("extra_position", -1L)
        if (vlcPosition > 0) return vlcPosition

        // MX Player / Just Player / mpv use Int extras
        val mxPosition = data.getIntExtra("position", -1)
        if (mxPosition > 0) return mxPosition.toLong()

        return null
    }

    private fun parseDuration(data: Intent): Long? {
        val vlcDuration = data.getLongExtra("extra_duration", -1L)
        if (vlcDuration > 0) return vlcDuration

        val mxDuration = data.getIntExtra("duration", -1)
        if (mxDuration > 0) return mxDuration.toLong()

        return null
    }

    private fun parseEndReason(data: Intent): Boolean {
        // MX Player returns "end_by" with values "user" or "playback_completion"
        val endBy = data.getStringExtra("end_by")
        return endBy != "playback_completion"
    }

    private fun matchesLanguage(lang: String?, target: String): Boolean {
        if (lang.isNullOrBlank()) return false
        val normalizedLang = lang.trim().lowercase()
        val normalizedTarget = target.trim().lowercase()

        if (com.nuvio.tv.ui.screens.player.PlayerSubtitleUtils.matchesLanguageCode(normalizedLang, normalizedTarget)) return true

        try {
            val targetName = com.nuvio.tv.ui.util.languageCodeToName(normalizedTarget).lowercase()
            if (targetName.isNotBlank() && normalizedLang.contains(targetName)) return true
        } catch (e: Exception) {
            // ignore
        }

        return false
    }
}
