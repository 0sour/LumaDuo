package com.sour.lumaduo.preferences

import android.content.Context
import androidx.preference.DialogPreference
import android.util.AttributeSet

import com.sour.lumaduo.R
import com.sour.lumaduo.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE

class ConfirmDeleteOscPreference : DialogPreference {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) :
        super(context, attrs)

    constructor(context: Context) :
        super(context)
}
