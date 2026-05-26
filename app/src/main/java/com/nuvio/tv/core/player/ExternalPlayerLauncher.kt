package com.nuvio.tv.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.nuvio.tv.R

object ExternalPlayerLauncher {

    /**
     * Fire-and-forget launch of an external player.
     * Used as a fallback when ActivityResultLauncher is not available (e.g. non-Activity context).
     */
    fun launch(
        context: Context,
        url: String,
        title: String? = null,
        headers: Map<String, String>? = null,
        resumePositionMs: Long = 0L,
        subtitles: List<SubtitleInput>? = null,
        selectedSubtitleIndex: Int = -1,
        preferredLanguages: List<String> = emptyList()
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")

                title?.let {
                    putExtra("title", it)
                    putExtra(Intent.EXTRA_TITLE, it)
                }

                headers?.let { hdrs ->
                    if (hdrs.isNotEmpty()) {
                        val headerArray = hdrs.entries.map { "${it.key}: ${it.value}" }.toTypedArray()
                        putExtra("headers", headerArray)
                    }
                }

                if (resumePositionMs > 0L) {
                    putExtra("position", resumePositionMs.toInt())
                    putExtra("from_start", false)
                }

                // Inject subtitle extras for external players
                if (!subtitles.isNullOrEmpty()) {
                    val subtitleUris = subtitles.map { Uri.parse(it.url) }.toTypedArray()
                    val subtitleNames = subtitles.map { it.name }.toTypedArray()
                    val subtitleFilenames = subtitles.map { "${it.lang}_${it.name}.srt" }.toTypedArray()

                    // 1. MX Player / Nova / mpv-android
                    putExtra("subs", subtitleUris)
                    putExtra("subs.name", subtitleNames)
                    putExtra("subs.filename", subtitleFilenames)

                    val enabledIndex = if (selectedSubtitleIndex in subtitles.indices) {
                        selectedSubtitleIndex
                    } else {
                        var foundIndex = -1
                        for (target in preferredLanguages) {
                            val idx = subtitles.indexOfFirst { matchesLanguage(it.lang, target) }
                            if (idx >= 0) {
                                foundIndex = idx
                                break
                            }
                        }
                        if (foundIndex >= 0) foundIndex else 0
                    }

                    if (enabledIndex in subtitles.indices) {
                        putExtra("subs.enable", arrayOf(Uri.parse(subtitles[enabledIndex].url)))
                    }

                    // 2. Just Player (ExoPlayer arrays and active track)
                    val activeSub = subtitles.getOrNull(enabledIndex)
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

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.player_no_external_player),
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * Create an [ExternalPlayerInput] for use with [ExternalPlayerResultContract].
     * Prefer this over [launch] when you have an Activity context and want to receive
     * playback progress back from the external player.
     */
    fun createInput(
        url: String,
        title: String? = null,
        headers: Map<String, String>? = null,
        resumePositionMs: Long = 0L,
        subtitles: List<SubtitleInput>? = null,
        selectedSubtitleIndex: Int = -1,
        preferredLanguages: List<String> = emptyList()
    ): ExternalPlayerInput = ExternalPlayerInput(
        url = url,
        title = title,
        headers = headers,
        resumePositionMs = resumePositionMs,
        subtitles = subtitles,
        selectedSubtitleIndex = selectedSubtitleIndex,
        preferredLanguages = preferredLanguages
    )

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
