package com.ganim.scanfire.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Google's on-device document scanner (Play services): live edge detection, auto-capture,
 * perspective correction, its own filters and a stain/finger "clean" tool. Free, offline,
 * and no camera code of our own to maintain.
 *
 * Returns a launcher; [onPages] gets the page JPEG uris, which are only valid until the next scan,
 * so callers copy them straight into the document.
 */
@Composable
fun rememberDocumentScanner(pageLimit: Int = 0, onPages: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPages)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) callback.value(uris)
        }
    }
    return {
        val activity = context.findActivity()
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .apply { if (pageLimit > 0) setPageLimit(pageLimit) }
            .build()
        if (activity != null) {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener {
                    Toast.makeText(context, "Scanner unavailable: ${it.message ?: "Google Play services is required"}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

/** System photo picker: no storage permission needed, works on every API level we support. */
@Composable
fun rememberImagePicker(onImages: (List<Uri>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onImages)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) callback.value(uris)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
