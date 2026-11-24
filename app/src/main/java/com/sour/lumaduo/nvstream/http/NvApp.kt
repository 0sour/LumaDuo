package com.sour.lumaduo.nvstream.http

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.sour.lumaduo.LimeLog

class NvApp {
    var appName: String = ""
    var appId: Int = 0
        set(value) {
            field = value
            initialized = true
        }
    var initialized: Boolean = false
        private set
    var hdrSupported: Boolean = false

    /**
     * Foundation Sunshine's additive per-app facts from /applist.
     * Other hosts leave this empty; callers must treat every value as optional.
     */
    val fsExtras: MutableMap<String, String> = linkedMapOf()

    val fsKind: String?
        get() = fsExtras["FsKind"]?.trim()?.takeIf { it.isNotEmpty() }

    val fsSource: String?
        get() = fsExtras["FsSource"]?.trim()?.takeIf { it.isNotEmpty() }

    val fsDefault: Boolean
        get() = fsExtras["FsDefault"]?.let { it == "1" || it.equals("true", ignoreCase = true) } == true

    val fsLastPlayed: Long?
        get() = fsExtras["FsLastPlayed"]?.toLongOrNull()?.takeIf { it > 0 }

    val fsPlaySeconds: Long?
        get() = fsExtras["FsPlaySeconds"]?.toLongOrNull()?.takeIf { it >= 0 }

    val fsArtVersion: String?
        get() = fsExtras["FsArtVersion"]?.trim()?.takeIf { it.isNotEmpty() }

    val fsArtTypes: Set<String>
        get() = fsExtras["FsArt"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    fun hasFsArt(type: String): Boolean = fsArtTypes.contains(type)

    var cmdList: JsonArray? = null

    constructor()

    constructor(appName: String) {
        this.appName = appName
    }

    constructor(appName: String, appId: Int, hdrSupported: Boolean) {
        this.appName = appName
        this.appId = appId
        this.hdrSupported = hdrSupported
    }

    fun setAppId(appId: String) {
        try {
            this.appId = appId.toInt()
        } catch (e: NumberFormatException) {
            LimeLog.warning("Malformed app ID: $appId")
        }
    }

    fun isHdrSupported(): Boolean = this.hdrSupported

    fun isInitialized(): Boolean = this.initialized

    fun setCmdList(cmdList: String) {
        // SuperCmds is already a JSON tree; avoid Gson's TypeToken-based object adapter path.
        if (cmdList.isBlank()) {
            this.cmdList = null
            return
        }
        val commands = JsonParser.parseString(cmdList)
        if (!commands.isJsonArray) {
            throw JsonSyntaxException("Expected a JSON array for SuperCmds")
        }
        this.cmdList = commands.asJsonArray
    }

    override fun toString(): String {
        return buildString {
            append("Name: ").append(appName).append("\n")
            append("HDR Supported: ").append(if (hdrSupported) "Yes" else "Unknown").append("\n")
            append("ID: ").append(appId).append("\n")
            if (cmdList != null) append("Super CMDs: ").append(cmdList.toString()).append("\n")
        }
    }

    companion object {
        // Foundation Sunshine PR #628 (commit b1c9888) defines this as proc::DESKTOP_APP_ID and
        // advertises support with /serverinfo DesktopSpecialAppSupport=1:
        // https://github.com/AlkaidLab/foundation-sunshine/pull/628
        const val DESKTOP_APP_ID = Int.MAX_VALUE
        const val DESKTOP_APP_NAME = "Desktop"
    }
}
