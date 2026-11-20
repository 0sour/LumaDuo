package com.sour.lumaduo.binding.input.driver

import com.sour.lumaduo.utils.CompletionSignal

/** One service stop operation; callers serialize updates with the session lock. */
internal class UsbDriverStopResult {
    val completion = CompletionSignal()
    private var failure: Throwable? = null

    fun failed(error: Throwable) {
        if (failure == null) failure = error
    }

    fun finish() {
        val error = failure
        if (error == null) completion.complete()
        else completion.completeExceptionally(error)
    }
}
