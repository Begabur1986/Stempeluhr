package com.example.stempeluhr.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.example.stempeluhr.R
import com.example.stempeluhr.data.core.ArbeitszeitManager

class StempeluhrWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_START_STOP = "com.example.stempeluhr.ACTION_START_STOP"
        private const val ACTION_PAUSE      = "com.example.stempeluhr.ACTION_PAUSE"
        private const val ACTION_TICK       = "com.example.stempeluhr.ACTION_TICK"
        private const val ACTION_UPDATE     = "com.example.stempeluhr.ACTION_APPWIDGET_UPDATE"

        fun updateWidget(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StempeluhrWidget::class.java))
            if (ids.isEmpty()) return
            val views = safeBuildRemoteViews(context)
            mgr.updateAppWidget(ids, views)
        }

        /** Baut RemoteViews „crash-sicher“. Bei Fehlern Placeholder setzen, Buttons immer verdrahten. */
        private fun safeBuildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_stempeluhr)

            // Default-Placeholder setzen (werden gleich ggf. überschrieben)
            views.setTextViewText(R.id.textRestzeit, "Rest: —")
            views.setTextViewText(R.id.textLetzterEintrag, "—")
            views.setTextViewText(R.id.btnArbeitszeit, "Start")
            views.setTextViewText(R.id.btnPause, "Pause Start")

            // Buttons IMMER verdrahten, selbst wenn Snapshot später fehlschlägt
            views.setOnClickPendingIntent(R.id.btnArbeitszeit, getPI(context, ACTION_START_STOP))
            views.setOnClickPendingIntent(R.id.btnPause, getPI(context, ACTION_PAUSE))

            try {
                // Tageswechsel abfangen
                try { ArbeitszeitManager.ensureRolloverIfNeeded(context) } catch (_: Throwable) {}

                val snap = ArbeitszeitManager.snapshot(context)
                // Inhalte
                views.setTextViewText(
                    R.id.textRestzeit,
                    "Rest: ${ArbeitszeitManager.formatHM(snap.weekRestMin)}"
                )
                views.setTextViewText(
                    R.id.textLetzterEintrag,
                    snap.lastEntry ?: "Noch keine Einträge"
                )
                views.setTextViewText(
                    R.id.btnArbeitszeit,
                    if (snap.runningWork) "Feierabend" else "Start"
                )
                views.setTextViewText(
                    R.id.btnPause,
                    if (snap.runningPause) "Pause Stop" else "Pause Start"
                )
            } catch (_: Throwable) {
                // Falls irgendwas schiefgeht, bleiben die Placeholder + Buttons erhalten.
            }
            return views
        }

        private fun getPI(context: Context, action: String): PendingIntent {
            val intent = Intent(context, StempeluhrWidget::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleTick(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = getPI(context, ACTION_TICK)
                am.cancel(pi)
                val triggerAt = SystemClock.elapsedRealtime() + 60_000L
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } catch (_: Throwable) { /* Widget tickt dann halt nicht, aber crasht nie */ }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleTick(context)
        updateWidget(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Host ruft das – wir refreshen sicher
        updateWidget(context)
        scheduleTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_START_STOP -> {
                // Robust: Snapshot in try-catch
                val running = try { ArbeitszeitManager.snapshot(context).runningWork } catch (_: Throwable) { false }
                if (running) {
                    val msg = try { ArbeitszeitManager.stopArbeitszeit(context) } catch (t: Throwable) {
                        "Fehler beim Stop";
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    try { ArbeitszeitManager.startArbeitszeit(context) } catch (_: Throwable) {}
                }
                updateWidget(context); scheduleTick(context)
            }

            ACTION_PAUSE -> {
                val runningPause = try { ArbeitszeitManager.snapshot(context).runningPause } catch (_: Throwable) { false }
                if (runningPause) {
                    val msg = try { ArbeitszeitManager.stopPause(context) } catch (t: Throwable) {
                        "Fehler beim Pausen-Stopp"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    try { ArbeitszeitManager.startPause(context) } catch (_: Throwable) {}
                }
                updateWidget(context); scheduleTick(context)
            }

            ACTION_TICK,
            ACTION_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateWidget(context); scheduleTick(context)
            }
        }
    }
}
