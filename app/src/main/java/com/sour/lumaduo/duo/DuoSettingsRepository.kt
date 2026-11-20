// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.content.Context
import androidx.preference.PreferenceManager
import com.sour.lumaduo.binding.input.haptics.GameRumbleMode
import com.sour.lumaduo.nvstream.jni.MoonBridge
import com.sour.lumaduo.preferences.BackgroundStreamBehavior
import com.sour.lumaduo.preferences.LegacySettingsModeStore
import com.sour.lumaduo.preferences.PreferenceConfiguration
import com.sour.lumaduo.preferences.QuitBehavior
import com.sour.lumaduo.preferences.TouchModePreferencePolicy
import com.sour.lumaduo.preferences.TouchModePreset
import com.sour.lumaduo.preferences.TouchModePreferenceState

/**
 * Read/write bridge between the LumaDuo bottom-screen settings page and V+'s
 * [PreferenceConfiguration].
 *
 * Scene fields are persisted with [PreferenceConfiguration.writeScenePreferences] so editing
 * one display option never rewrites unrelated input/audio/UI preferences. Audio and rumble
 * are single public keys, so they are written directly.
 */
class DuoSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val legacyStore = LegacySettingsModeStore(prefs)

    data class Snapshot(
        val resolutionLabel: String,
        val resolutionValue: String,
        val isNativeResolution: Boolean,
        val fps: Int,
        val bitrateMbps: Int,
        val formatLabel: String,
        val hdr: Boolean,
        val adaptiveBitrate: Boolean,
        val audioLabel: String,
        val rumbleLabel: String,
    )

    enum class Field {
        RESOLUTION,
        FPS,
        BITRATE,
        FORMAT,
        HDR,
        ADAPTIVE_BITRATE,
        AUDIO,
        RUMBLE,
    }

    fun read(): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        return Snapshot(
            resolutionLabel = if (config.isNativeResolution) {
                "原生 · ${config.width}×${config.height}"
            } else {
                "${config.width} × ${config.height}"
            },
            resolutionValue = if (config.isNativeResolution) {
                PreferenceConfiguration.RES_NATIVE
            } else {
                "${config.width}x${config.height}"
            },
            isNativeResolution = config.isNativeResolution,
            fps = config.fps,
            bitrateMbps = config.bitrate / 1000,
            formatLabel = formatLabel(config.videoFormat),
            hdr = config.enableHdr,
            adaptiveBitrate = config.enableAdaptiveBitrate,
            audioLabel = audioLabel(prefs.getString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "2")),
            rumbleLabel = rumbleLabel(
                prefs.getString(
                    PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING,
                    GameRumbleMode.CONTROLLER.preferenceValue,
                ),
            ),
        )
    }

    /**
     * Adjust one field and return the persisted snapshot. [delta] is +1/-1 for list fields;
     * bitrate moves in 5 Mbps steps and booleans toggle.
     */
    fun adjust(field: Field, delta: Int): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        when (field) {
            Field.RESOLUTION -> {
                val options = resolutionOptions(config.width, config.height, config.isNativeResolution)
                val currentIndex = options.indexOfFirst {
                    if (it.native) config.isNativeResolution
                    else !config.isNativeResolution && it.width == config.width && it.height == config.height
                }.coerceAtLeast(0)
                val next = options[wrapIndex(currentIndex + delta, options.size)]
                config.isNativeResolution = next.native
                config.isCustomResolution = false
                if (!next.native) {
                    config.width = next.width
                    config.height = next.height
                }
                config.writeScenePreferences(appContext)
            }
            Field.FPS -> {
                val options = (listOf(30, 60, 90, 120) + config.fps).distinct().sorted()
                val index = options.indexOf(config.fps).coerceAtLeast(0)
                config.fps = options[wrapIndex(index + delta, options.size)]
                config.writeScenePreferences(appContext)
            }
            Field.BITRATE -> {
                // PreferenceConfiguration stores bitrate in Kbps; the shell edits in 5 Mbps steps.
                config.bitrate = (config.bitrate + delta * 5_000).coerceIn(5_000, 150_000)
                config.writeScenePreferences(appContext)
            }
            Field.FORMAT -> {
                val options = PreferenceConfiguration.FormatOption.entries
                val index = options.indexOf(config.videoFormat).coerceAtLeast(0)
                config.videoFormat = options[wrapIndex(index + delta, options.size)]
                config.writeScenePreferences(appContext)
            }
            Field.HDR -> {
                config.enableHdr = !config.enableHdr
                // hdrMode=0 (SDR) would override enableHdr at stream start.
                if (config.enableHdr && config.hdrMode == MoonBridge.HDR_MODE_SDR) {
                    config.hdrMode = MoonBridge.HDR_MODE_HDR10
                }
                config.writeScenePreferences(appContext)
            }
            Field.ADAPTIVE_BITRATE -> {
                config.enableAdaptiveBitrate = !config.enableAdaptiveBitrate
                config.writeScenePreferences(appContext)
            }
            Field.AUDIO -> {
                val values = listOf("2", "51", "71", "714")
                val current = prefs.getString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, "2")
                val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
                prefs.edit()
                    .putString(
                        PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING,
                        values[wrapIndex(index + delta, values.size)],
                    )
                    .apply()
            }
            Field.RUMBLE -> {
                val options = GameRumbleMode.entries
                val current = GameRumbleMode.fromPreferenceValue(
                    prefs.getString(
                        PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING,
                        GameRumbleMode.CONTROLLER.preferenceValue,
                    ),
                )
                val index = options.indexOf(current).coerceAtLeast(0)
                prefs.edit()
                    .putString(
                        PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING,
                        options[wrapIndex(index + delta, options.size)].preferenceValue,
                    )
                    .apply()
            }
        }
        return read()
    }

    // ==================== Non-scene settings ====================

    fun setResolution(width: Int, height: Int, native: Boolean): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.isNativeResolution = native
        config.isCustomResolution = false
        if (!native) {
            config.width = width
            config.height = height
        }
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setFps(fps: Int): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.fps = fps
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setBitrateMbps(mbps: Int): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.bitrate = (mbps * 1000).coerceIn(500, 800_000)
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setFormat(format: PreferenceConfiguration.FormatOption): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.videoFormat = format
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setHdr(enabled: Boolean): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.enableHdr = enabled
        if (enabled && config.hdrMode == MoonBridge.HDR_MODE_SDR) {
            config.hdrMode = MoonBridge.HDR_MODE_HDR10
        }
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setAdaptive(enabled: Boolean): Snapshot {
        val config = PreferenceConfiguration.readPreferences(appContext)
        config.enableAdaptiveBitrate = enabled
        config.writeScenePreferences(appContext)
        return read()
    }

    fun setAudio(value: String): Snapshot {
        prefs.edit().putString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, value).apply()
        return read()
    }

    fun setRumble(value: String): Snapshot {
        prefs.edit().putString(PreferenceConfiguration.GAME_RUMBLE_MODE_PREF_STRING, value).apply()
        return read()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun sceneConfig(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(appContext)

    internal fun backgroundMode(): BackgroundStreamBehavior = legacyStore.backgroundMode()

    internal fun setBackgroundMode(mode: BackgroundStreamBehavior) = legacyStore.setBackgroundMode(mode)

    internal fun quitMode(): QuitBehavior = legacyStore.quitMode()

    internal fun setQuitMode(mode: QuitBehavior) = legacyStore.setQuitMode(mode)

    internal fun touchPreset(): TouchModePreset = TouchModePreferencePolicy.presetFor(

        legacyStore.touchState(hasTouchscreen = true),
    )
    internal fun setTouchPreset(preset: TouchModePreset): TouchModePreferenceState =

        legacyStore.setTouchPreset(preset)

    private data class ResolutionOption(
        val width: Int,
        val height: Int,
        val native: Boolean,
    )

    private fun resolutionOptions(
        currentWidth: Int,
        currentHeight: Int,
        currentNative: Boolean,
    ): List<ResolutionOption> {
        val fixed = listOf(
            ResolutionOption(1280, 720, false),
            ResolutionOption(1920, 1080, false),
            ResolutionOption(2560, 1440, false),
            ResolutionOption(3840, 2160, false),
        ).toMutableList()
        if (!currentNative && fixed.none { it.width == currentWidth && it.height == currentHeight }) {
            fixed += ResolutionOption(currentWidth, currentHeight, false)
        }
        fixed.sortWith(compareBy({ it.width.toLong() * it.height }, { it.width }))
        return fixed + ResolutionOption(0, 0, true)
    }

    private fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size

    companion object {
        fun formatLabel(format: PreferenceConfiguration.FormatOption): String = when (format) {
            PreferenceConfiguration.FormatOption.AUTO -> "自动"
            PreferenceConfiguration.FormatOption.FORCE_PYROWAVE -> "Pyrowave"
            PreferenceConfiguration.FormatOption.FORCE_AV1 -> "AV1"
            PreferenceConfiguration.FormatOption.FORCE_HEVC -> "HEVC"
            PreferenceConfiguration.FormatOption.FORCE_H264 -> "H.264"
        }

        fun audioLabel(value: String?): String = when (value) {
            "51" -> "5.1 环绕"
            "71" -> "7.1 环绕"
            "714" -> "7.1.4 环绕"
            else -> "立体声"
        }

        fun rumbleLabel(value: String?): String = when (GameRumbleMode.fromPreferenceValue(value)) {
            GameRumbleMode.COORDINATED -> "智能分配"
            GameRumbleMode.DEVICE -> "仅本机"
            GameRumbleMode.CONTROLLER -> "仅手柄"
        }
    }
}
