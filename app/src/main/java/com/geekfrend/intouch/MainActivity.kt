package com.geekfrend.intouch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.geekfrend.intouch.ui.InTouchNav
import com.geekfrend.intouch.ui.InTouchViewModel
import com.geekfrend.intouch.ui.theme.AppTheme
import com.geekfrend.intouch.ui.theme.InTouchTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contactId"
    }

    private val vm: InTouchViewModel by viewModels()
    private val openContact = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBars(vm.theme())
        if (savedInstanceState == null) handle(intent)
        setContent {
            // Re-read the theme whenever a setting changes, so switching in Settings applies at once.
            val version by vm.settingsVersion.collectAsState()
            val theme = remember(version) { vm.theme() }
            LaunchedEffect(theme) { applyBars(theme) }
            InTouchTheme(theme) {
                InTouchNav(vm, openContact, onOpenHandled = { openContact.value = null })
            }
        }
    }

    /** Light status-bar icons on Drifter's night, dark ones on Monument's sand. */
    private fun applyBars(theme: AppTheme) {
        val style = if (theme == AppTheme.MONUMENT) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CONTACT_ID)?.let { openContact.value = it }
    }
}
