package com.ganim.scanfire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ganim.scanfire.data.Settings
import com.ganim.scanfire.security.AppLock
import com.ganim.scanfire.ui.LockScreen
import com.ganim.scanfire.ui.ScanFireRoot
import com.ganim.scanfire.ui.common.container
import com.ganim.scanfire.ui.theme.ScanFireTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Something handed to ScanFire from outside: a share, an "open with", or a launcher shortcut. */
sealed interface Incoming {
    data class Images(val uris: List<Uri>) : Incoming
    data class Pdf(val uri: Uri) : Incoming
    data object Scan : Incoming
    data object ScanCode : Incoming
}

// FragmentActivity (still a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    val incoming = MutableStateFlow<Incoming?>(null)

    /** Starts locked until settings say otherwise, so content never flashes before the prompt. */
    private var locked by mutableStateOf(true)
    private var lockEnabled = false
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = parse(intent)

        lifecycleScope.launch {
            lockEnabled = container.settings.settings.first().appLock
            locked = lockEnabled && savedInstanceState?.getBoolean(KEY_UNLOCKED) != true
            if (locked) unlock()
        }
        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                lockEnabled = s.appLock
                // Keep document contents out of the recent-apps thumbnail when the lock is on.
                if (s.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                if (!s.appLock) locked = false
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            ScanFireTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                if (locked) LockScreen(onUnlock = ::unlock) else ScanFireRoot(incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { incoming.value = it }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lockEnabled && !locked && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > AppLock.RELOCK_AFTER_MS) {
            locked = true
            unlock()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    private fun unlock() {
        AppLock.prompt(this, onSuccess = { locked = false })
    }

    private fun parse(intent: Intent?): Incoming? {
        intent ?: return null
        val type = intent.type.orEmpty()
        return when (intent.action) {
            ACTION_SCAN -> Incoming.Scan
            ACTION_SCAN_CODE -> Incoming.ScanCode
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                if (type == "application/pdf") Incoming.Pdf(uri) else Incoming.Images(listOf(uri))
            }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.isNotEmpty() }?.let { Incoming.Images(it) }
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (type == "application/pdf" || uri.path?.endsWith(".pdf", true) == true) Incoming.Pdf(uri) else Incoming.Images(listOf(uri))
            }
            else -> null
        }
    }

    companion object {
        const val ACTION_SCAN = "com.ganim.scanfire.action.SCAN"
        const val ACTION_SCAN_CODE = "com.ganim.scanfire.action.SCAN_CODE"
        private const val KEY_UNLOCKED = "unlocked"
    }
}
