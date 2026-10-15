package com.geekfrend.intouch.lab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SOURCE = "source"
    }

    private val resumeTick = mutableIntStateOf(0)
    private val source = mutableStateOf("launcher")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source.value = intent?.getStringExtra(EXTRA_SOURCE) ?: "launcher"
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LabScreen(tick = resumeTick.intValue, source = source.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        source.value = intent.getStringExtra(EXTRA_SOURCE) ?: "launcher"
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private val PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.CALL_PHONE,
)

private fun granted(ctx: Context, permission: String) =
    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

private fun displayIdOf(activity: Activity): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        activity.display?.displayId ?: -1
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.displayId
    }

@Composable
private fun LabScreen(tick: Int, source: String) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val config = LocalConfiguration.current
    var refresh by remember { mutableIntStateOf(0) }
    val key = tick + refresh

    val picked = remember(key) { Store.picked(ctx) }
    val perms = remember(key) { PERMISSIONS.associateWith { granted(ctx, it) } }
    val calls = remember(key) {
        if (granted(ctx, Manifest.permission.READ_CALL_LOG)) {
            runCatching { Device.recentCalls(ctx, 20) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
    }
    val events = remember(key) { Store.events(ctx).take(25) }
    val listenerOn = remember(key) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    var message by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val p = runCatching { Device.readContact(ctx, uri) }.getOrNull()
            message = if (p != null) {
                Store.savePicked(ctx, p)
                TestWidget.refreshAll(ctx)
                "Test contact set: ${p.name}"
            } else {
                "Couldn't read that contact. Does it have a phone number?"
            }
        }
        refresh++
    }

    fun launch(label: String, intent: Intent?) {
        val result = if (intent == null) {
            "not available"
        } else {
            try {
                ctx.startActivity(intent)
                "opened"
            } catch (e: Exception) {
                "failed: ${e.javaClass.simpleName}"
            }
        }
        Store.setNote(ctx, label, result)
        message = "$label: $result"
        refresh++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("InTouch Lab", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Opened from: $source · screen ${displayIdOf(activity)} · " +
                "${config.screenWidthDp}×${config.screenHeightDp} dp",
            style = MaterialTheme.typography.bodySmall,
        )
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        Section("1. Permissions") {
            perms.forEach { (permission, ok) ->
                Text("${if (ok) "✓" else "✗"} ${permission.substringAfterLast('.')}")
            }
            if (perms.values.any { !it }) {
                Button(onClick = { permissionLauncher.launch(PERMISSIONS) }) { Text("Grant permissions") }
            }
        }

        Section("2. Test contact") {
            if (picked == null) {
                Text("Pick someone who uses WhatsApp and will answer a test call.")
            } else {
                Text("${picked.name} · ${picked.number}", fontWeight = FontWeight.SemiBold)
                Text("WhatsApp voice entry: ${if (picked.waDataId != null) "found" else "not found"}")
                if (picked.waMimes.isNotEmpty()) {
                    Text(
                        "WhatsApp entries: " + picked.waMimes.joinToString { it.substringAfterLast('.') },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(onClick = {
                if (granted(ctx, Manifest.permission.READ_CONTACTS)) {
                    pickLauncher.launch(null)
                } else {
                    permissionLauncher.launch(PERMISSIONS)
                }
            }) { Text(if (picked == null) "Pick a contact" else "Pick a different contact") }
        }

        Section("3. Start calls") {
            val p = picked
            if (p == null) {
                Text("Pick a test contact first.")
            } else {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val intent = if (granted(ctx, Manifest.permission.CALL_PHONE)) {
                        Device.phoneCallIntent(p.number)
                    } else {
                        Device.dialIntent(p.number)
                    }
                    launch("Phone call", intent)
                }) { Text("Phone call") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    launch("WhatsApp voice call", Device.waVoiceIntent(p))
                }) { Text("WhatsApp voice call") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    launch("WhatsApp chat", Device.waChatIntent(p.number))
                }) { Text("Open WhatsApp chat") }
                listOf("Phone call", "WhatsApp voice call", "WhatsApp chat").forEach { label ->
                    val note = remember(key) { Store.note(ctx, label) }
                    if (note.isNotEmpty()) Text("$label: $note", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Section("4. Call history") {
            Text(
                "Test: call your test contact and hang up before they answer, after about 20 seconds " +
                    "of ringing. Then come back here and check that call's length.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!granted(ctx, Manifest.permission.READ_CALL_LOG)) {
                Text("Needs the call history permission (section 1).")
            } else if (calls.isEmpty()) {
                Text("No calls found.")
            } else {
                calls.forEach { r ->
                    val isTest = picked != null && Device.sameNumber(r.number, picked.number)
                    val who = r.name?.takeIf { it.isNotBlank() } ?: Device.mask(r.number)
                    val short = r.type == android.provider.CallLog.Calls.OUTGOING_TYPE &&
                        r.durationSec in 1..29
                    Text(
                        "${Device.time(r.date)} · ${Device.typeLabel(r.type)} · ${r.durationSec} s · $who" +
                            (if (short) "  ← under 30 s" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isTest) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Section("5. WhatsApp notifications") {
            Text(if (listenerOn) "✓ Notification access is on" else "✗ Notification access is off")
            if (!listenerOn) {
                Text(
                    "Android blocks this setting for apps installed from a browser. If the switch is " +
                        "greyed out, open App info, tap ⋮ (top right), choose \"Allow restricted settings\", " +
                        "then try again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    launch("Notification settings", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open notification access settings") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    launch(
                        "App info",
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                    )
                }) { Text("Open App info") }
            }
            Text(
                "Test: have a WhatsApp voice call with your test contact (incoming and outgoing), then check here.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (events.isEmpty()) {
                Text("Nothing recorded yet.")
            } else {
                events.forEach { e ->
                    Text(
                        "${Device.time(e.optLong("t"))} · ${e.optString("event")} · " +
                            "${e.optString("title")} · ${e.optString("text")} · " +
                            "category=${e.optString("category")} · channel=${e.optString("channel")} · " +
                            "ongoing=${e.optBoolean("ongoing")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (e.optBoolean("call")) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                OutlinedButton(onClick = {
                    Store.clearEvents(ctx)
                    refresh++
                }) { Text("Clear") }
            }
        }

        Section("6. Cover screen") {
            Text(
                "Add the widget: Settings → Cover screen → Widgets → InTouch Lab test. Then close the phone " +
                    "and try each button. \"Open here\" should open this screen on the cover screen; " +
                    "the line at the top then says \"widget, Open here\" and the screen size.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                TestWidget.refreshAll(ctx)
                message = "Widget refreshed"
            }) { Text("Refresh widget") }
        }

        Section("7. What worked?") {
            CHECKS.forEach { (id, label) -> CheckItem(id, label, key) { refresh++ } }
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "InTouch Lab results")
                .putExtra(Intent.EXTRA_TEXT, Report.build(ctx))
            ctx.startActivity(Intent.createChooser(send, "Share results"))
        }) { Text("Share results") }
        Text(
            "Shared results hide phone numbers (last 4 digits only) and replace names with " +
                "[test contact] or [someone else].",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CheckItem(id: String, label: String, key: Int, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val value = remember(key) { Store.check(ctx, id) }
    Text(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("yes" to "Worked", "no" to "Didn't work").forEach { (answer, text) ->
            if (value == answer) {
                Button(onClick = {
                    Store.setCheck(ctx, id, "")
                    onChange()
                }) { Text(text) }
            } else {
                OutlinedButton(onClick = {
                    Store.setCheck(ctx, id, answer)
                    onChange()
                }) { Text(text) }
            }
        }
    }
}
