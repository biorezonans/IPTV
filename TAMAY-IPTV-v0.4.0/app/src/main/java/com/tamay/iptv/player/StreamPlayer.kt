package com.tamay.iptv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class StreamPlayer(
    context: Context,
    private val playerView: PlayerView,
    private val onError: (String) -> Unit
) {
    private val player = ExoPlayer.Builder(context).build().also { exo ->
        playerView.player = exo
        exo.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError(error.localizedMessage ?: "Bilinmeyen oynatma hatası")
            }
        })
    }

    fun play(url: String) {
        val uri = Uri.parse(url)
        val builder = MediaItem.Builder().setUri(uri)
        val path = uri.lastPathSegment.orEmpty().lowercase()
        if (path.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }

    /** Toggles text tracks. Returns true when subtitle tracks are enabled. */
    fun toggleSubtitles(): Boolean {
        val currentlyDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val enable = currentlyDisabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enable)
            .build()
        return enable
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        playerView.player = null
        player.release()
    }
}
