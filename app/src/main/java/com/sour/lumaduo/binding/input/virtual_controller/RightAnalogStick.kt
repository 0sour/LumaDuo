/**
 * Created by Karim Mreisi.
 */
package com.sour.lumaduo.binding.input.virtual_controller

import android.content.Context
import com.sour.lumaduo.binding.input.virtual_controller.VirtualControllerElement.Companion.EID_RS
import com.sour.lumaduo.nvstream.input.ControllerPacket

class RightAnalogStick(controller: VirtualController, context: Context) :
    AnalogStick(controller, context, EID_RS) {

    init {
        addAnalogStickListener(object : AnalogStickListener {
            override fun onMovement(x: Float, y: Float) {
                val inputContext = controller.controllerInputContext
                inputContext.rightStickX = (x * 0x7FFE).toInt().toShort()
                inputContext.rightStickY = (y * 0x7FFE).toInt().toShort()
                controller.sendControllerInputContext()
            }

            override fun onClick() {}

            override fun onDoubleClick() {
                controller.setButtonState(this, ControllerPacket.RS_CLK_FLAG, true)
            }

            override fun onRevoke() {
                controller.setButtonState(this, ControllerPacket.RS_CLK_FLAG, false)
            }
        })
    }
}
