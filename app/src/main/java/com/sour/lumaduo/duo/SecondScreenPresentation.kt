// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.duo

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
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

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val backDispatcher = OnBackPressedDispatcher()
    private val resultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            LimeLog.warning("LumaDuo bottom presentation has no ActivityResult host for request $requestCode")
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
    override val activityResultRegistry: ActivityResultRegistry get() = resultRegistry

    private val contentView = ComposeView(activity).apply {
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
            decorView.setBackgroundColor(Color.BLACK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        setContentView(contentView)
        LimeLog.info("LumaDuo bottom presentation created on display ${display.displayId}")
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        window?.decorView?.post { window?.decorView?.requestFocus() }
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun dismiss() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
        super.dismiss()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)
        ) {
            LimeLog.info("LumaDuo bottom presentation received back/escape")
            onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Finds the presentation display without hardcoding display 2. */
fun findPresentationDisplay(context: Context, excludeDisplayId: Int = Display.DEFAULT_DISPLAY): Display? {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        ?: return null
    return manager.displays.firstOrNull { display ->
        display.displayId != excludeDisplayId &&
            display.isValid &&
            (display.flags and Display.FLAG_PRESENTATION) != 0
    }
}
