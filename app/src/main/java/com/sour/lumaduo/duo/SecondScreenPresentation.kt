// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sour.lumaduo.LimeLog
import com.sour.lumaduo.ui.luma.LumaTheme

/**
 * Bottom-screen host for LumaDuo.
 *
 * A Presentation is a Dialog, not an Activity: it has no Lifecycle, ViewModelStore,
 * SavedStateRegistry, ActivityResultRegistry or OnBackPressedDispatcher. Compose and
 * viewModel() require those owners, so this class supplies them on the presentation's
 * decor view. Keep session/player state in the Activity; this host is disposable.
 *
 * RP Duo: bottom display is presentation-only (canHostTasks=false). AYN Thor can host
 * tasks, but the same Presentation path is used so both devices share one shell.
 */
class SecondScreenPresentation(
    activity: ComponentActivity,
    display: Display,
    private val onBack: () -> Unit = {},
    content: @Composable () -> Unit,
) : Presentation(activity, display),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner,
    ActivityResultRegistryOwner {

    private val hostActivity = activity
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val backDispatcher = OnBackPressedDispatcher().apply {
        // Activity-level fallback: page/overlay BackHandlers inside the Compose
        // shell are registered later and therefore run first.
        addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
    }
    // The Presentation has no ActivityResult host of its own. Delegate to the
    // Activity registry so SAF / permission / picker launchers inside the bottom
    // Compose tree can actually launch and receive a result.
    private val resultRegistry = activity.activityResultRegistry

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
    override val activityResultRegistry: ActivityResultRegistry get() = resultRegistry

    private var lastConfirmAt = 0L
    private var focusEnabled = true
    private var backInterceptionEnabled = true
    private var lastHatKeyCode = 0

    private val contentView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            LumaTheme {
                content()
            }
        }
    }

    init {
        setCancelable(false)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            decorView.setBackgroundColor(Color.BLACK)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        if (!focusEnabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeOnBackPressedDispatcherOwner(this)
        setContentView(contentView)
        LimeLog.info("LumaDuo bottom presentation created on display ${display.displayId}")
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (focusEnabled) {
            window?.decorView?.post { window?.decorView?.requestFocus() }
        }
    }

    override fun onStop() {
        // Dialog.dismiss() calls onStop from inside super.dismiss(). If dismiss() has
        // already moved the registry to DESTROYED, sending PAUSE/STOP would throw
        // "State is 'DESTROYED' and cannot be moved to STARTED".
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        super.onStop()
    }

    override fun dismiss() {
        // Let Dialog detach the window and deliver onStop while the registry is still
        // STARTED, then finish the owner lifecycle. This keeps Compose disposal and
        // ViewModelStore clearing in the expected order.
        super.dismiss()
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
    }

    /**
     * Controls whether this window takes key focus. The stream console sets this to false
     * in passthrough mode so the game window keeps receiving controller/keyboard input;
     * touch still works and is caught by the console route to enter local mode.
     */
    fun setFocusEnabled(enabled: Boolean) {
        if (focusEnabled == enabled) return
        focusEnabled = enabled
        val window = window ?: return
        if (enabled) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.decorView.isFocusableInTouchMode = true
            window.decorView.post { window.decorView.requestFocus() }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            hostActivity.window?.decorView?.requestFocus()
        }
    }

    fun setBackInterceptionEnabled(enabled: Boolean) {
        backInterceptionEnabled = enabled
    }

    /**
     * Some controllers report BUTTON_B and a synthesized DPAD_CENTER for one press.
     * Consume the second event so LumaFocusable/clickable cannot fire twice.
     */
    private fun consumeDuplicateConfirm(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B &&
            event.keyCode != KeyEvent.KEYCODE_DPAD_CENTER
        ) {
            return false
        }
        if (event.repeatCount > 0) return true
        val now = SystemClock.uptimeMillis()
        val duplicate = now - lastConfirmAt < 40L
        lastConfirmAt = now
        return duplicate
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!focusEnabled) return super.dispatchKeyEvent(event)
        if (!backInterceptionEnabled) return super.dispatchKeyEvent(event)
        if (consumeDuplicateConfirm(event)) return true
        // While a text field owns an IME connection, ordinary keys belong to it.
        // Face buttons keep local semantics: physical A closes, physical B confirms.
        val typing = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.isAcceptingText == true
        val faceKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B
        // While the system IME is typing, ordinary keys belong to the text field.
        // Face buttons keep their local semantics; B is passed to Compose as confirm.
        if (typing && !faceKey) {
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                // RP Duo physical A is the back/close key; B is confirm and must
                // reach the Compose focus tree.
                event.keyCode == KeyEvent.KEYCODE_BUTTON_A)
        ) {
            LimeLog.info("LumaDuo bottom presentation received back/escape/button-a")
            backDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Joystick HAT axes do not produce key events, but the Compose focus tree only
     * understands D-pad keys. Translate each new hat direction into one down/up pair.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!focusEnabled) return super.dispatchGenericMotionEvent(event)
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            return super.dispatchGenericMotionEvent(event)
        }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val keyCode = when {
            hatX > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
            hatX < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            hatY > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
            hatY < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
            else -> 0
        }
        if (keyCode == 0) {
            lastHatKeyCode = 0
            return super.dispatchGenericMotionEvent(event)
        }
        if (keyCode != lastHatKeyCode) {
            lastHatKeyCode = keyCode
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return true
    }
}

/** Finds the presentation display without hardcoding display 2. */
fun findPresentationDisplay(context: Context, excludeDisplayId: Int = Display.DEFAULT_DISPLAY): Display? {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        ?: return null
    return manager.displays
        .asSequence()
        .filter { display ->
            display.displayId != excludeDisplayId &&
                display.isValid &&
                (display.flags and Display.FLAG_PRESENTATION) != 0
        }
        // Stable choice if a device exposes more than one presentation display.
        .sortedBy { it.displayId }
        .firstOrNull()
}
