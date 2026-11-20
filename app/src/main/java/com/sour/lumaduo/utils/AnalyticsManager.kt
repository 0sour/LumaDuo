package com.sour.lumaduo.utils

import android.content.Context
import android.os.Bundle

/**
 * Local-only analytics facade.
 *
 * LumaDuo ships without Firebase/Crashlytics analytics. The legacy call sites still use
 * this API, so it keeps session timing locally but never sends an event anywhere.
 */
class AnalyticsManager private constructor(context: Context) {

    private var sessionStartTime: Long = 0
    private var isSessionActive = false

    fun startUsageTracking() {
        if (!isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
            isSessionActive = true
        }
    }

    fun stopUsageTracking() {
        isSessionActive = false
    }

    fun logGameStreamStart(computerName: String, appName: String?) = Unit

    fun logGameStreamEnd(computerName: String, appName: String?, durationMs: Long) = Unit

    fun logGameStreamEnd(
        computerName: String,
        appName: String?,
        effectiveDurationMs: Long,
        decoderMessage: String?,
        resolutionWidth: Int,
        resolutionHeight: Int,
        averageEndToEndLatency: Int,
        averageDecoderLatency: Int,
    ) = Unit

    fun logAppLaunch() = Unit

    fun logCustomEvent(eventName: String, parameters: Bundle?) = Unit

    fun setUserProperty(propertyName: String, propertyValue: String) = Unit

    fun isSessionActive(): Boolean = isSessionActive

    fun getCurrentSessionDuration(): Long =
        if (isSessionActive) System.currentTimeMillis() - sessionStartTime else 0

    fun cleanup() {
        isSessionActive = false
    }

    companion object {
        @Volatile
        private var instance: AnalyticsManager? = null

        fun getInstance(context: Context): AnalyticsManager =
            instance ?: synchronized(this) {
                instance ?: AnalyticsManager(context.applicationContext).also { instance = it }
            }
    }
}
