package com.ganim.scanfire.security

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock using the phone's own fingerprint/face/PIN, so ScanFire never stores a secret itself.
 * It relocks after the app has been in the background for [RELOCK_AFTER_MS].
 */
object AppLock {
    const val RELOCK_AFTER_MS = 30_000L

    private val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** True when the phone has a screen lock or biometric that can protect the app. */
    fun available(activity: FragmentActivity): Boolean {
        if (BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) return true
        // Before Android 11 the PIN fallback comes from setDeviceCredentialAllowed, which canAuthenticate can't see.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            activity.getSystemService(KeyguardManager::class.java).isDeviceSecure
    }

    fun prompt(activity: FragmentActivity, title: String = "Unlock ScanFire", onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}
