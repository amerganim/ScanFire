package com.ganim.scanfire.ui.common

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ganim.scanfire.AppContainer
import com.ganim.scanfire.ScanFireApp

val Context.container: AppContainer get() = (applicationContext as ScanFireApp).container

@Composable
fun appContainer(): AppContainer = LocalContext.current.container

/** ViewModel built from the [AppContainer]; [key] separates instances such as one per document. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun formatDate(context: Context, time: Long): String =
    DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

/** Strips characters that are illegal in file names on Android and common desktop systems. */
fun safeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().ifEmpty { "ScanFire" }.take(80)
