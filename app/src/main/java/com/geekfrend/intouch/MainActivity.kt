package com.geekfrend.intouch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.geekfrend.intouch.ui.InTouchNav
import com.geekfrend.intouch.ui.InTouchViewModel
import com.geekfrend.intouch.ui.theme.InTouchTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contactId"
    }

    private val vm: InTouchViewModel by viewModels()
    private val openContact = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            InTouchTheme {
                InTouchNav(vm, openContact, onOpenHandled = { openContact.value = null })
            }
        }
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
