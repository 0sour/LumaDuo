package com.sour.lumaduo.binding

import android.content.Context

import com.sour.lumaduo.binding.crypto.AndroidCryptoProvider
import com.sour.lumaduo.nvstream.http.LimelightCryptoProvider

object PlatformBinding {
    @JvmStatic
    fun getCryptoProvider(c: Context): LimelightCryptoProvider {
        return AndroidCryptoProvider(c)
    }
}
