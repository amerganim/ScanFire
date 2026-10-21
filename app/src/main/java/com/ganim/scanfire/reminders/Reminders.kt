package com.ganim.scanfire.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ganim.scanfire.MainActivity
import com.ganim.scanfire.R
import com.ganim.scanfire.data.db.DocumentEntity
import com.ganim.scanfire.data.db.ScanFireDatabase
import com.ganim.scanfire.ui.common.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * "Remind me" on a document: a notification on the chosen day (an ID or passport expiring, a bill due).
 * Uses an inexact AlarmManager alarm per document, so no exact-alarm permission and no background service.
 * Alarms are re-armed after a reboot or app update by [ReminderReceiver].
 */
class Reminders(private val context: Context, private val db: ScanFireDatabase) {

    fun all(): Flow<List<DocumentEntity>> = db.documents().observeReminders()

    suspend fun set(docId: Long, day: Long, note: String?) {
        val at = morningOf(day)
        db.documents().setReminder(docId, at, note?.trim()?.ifEmpty { null })
        schedule(docId, at)
    }

    suspend fun clear(docId: Long) {
        db.documents().setReminder(docId, null, null)
        alarms().cancel(pending(docId))
    }

    /** Re-arms every reminder still in the future. */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        db.documents().reminders().filter { (it.remindAt ?: 0) > now }.forEach { schedule(it.id, it.remindAt!!) }
    }

    private fun schedule(docId: Long, at: Long) {
        if (at <= System.currentTimeMillis()) return
        alarms().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(docId))
    }

    private fun alarms() = context.getSystemService(AlarmManager::class.java)

    private fun pending(docId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        docId.toInt(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_DOC, docId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_FIRE = "com.ganim.scanfire.action.REMINDER"
        const val EXTRA_DOC = "docId"
        private const val CHANNEL = "reminders"

        /** 9:00 local time on the day containing [day]. */
        fun morningOf(day: Long): Long = Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        internal fun notify(context: Context, doc: DocumentEntity) {
            if (!canNotify(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Document reminders", NotificationManager.IMPORTANCE_DEFAULT))
            // A vault document's name stays private, even on the lock screen.
            val title = if (doc.vault) "Reminder about a vault document" else doc.name
            val open = PendingIntent.getActivity(
                context,
                doc.id.toInt(),
                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOCUMENT).putExtra(EXTRA_DOC, doc.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(doc.remindNote ?: "You asked ScanFire to remind you about this today.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            NotificationManagerCompat.from(context).notify(doc.id.toInt(), notification)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = context.applicationContext.container
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        val doc = c.database.documents().get(intent.getLongExtra(Reminders.EXTRA_DOC, -1))
                        if (doc?.remindAt != null && doc.deletedAt == null) Reminders.notify(context, doc)
                    }
                    else -> c.reminders.rescheduleAll() // boot completed, app updated
                }
            } finally {
                result.finish()
            }
        }
    }
}
