package com.example.stempeluhr.data.core

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.example.stempeluhr.widget.StempeluhrWidget
import com.example.stempeluhr.util.cancelArbeitszeitNotification
import com.example.stempeluhr.util.showArbeitszeitNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Source of Truth:
 * - Start/Stop Arbeit & Pause
 * - Tageswechsel
 * - Kurzpausen & gesetzliche Pausen (gehen NUR in Rest ein)
 * - Berechnungen (Session/Rest)
 * - Snapshot fürs UI/Widget
 */
object ArbeitszeitManager {

    // ---------- Formats ----------
    private val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun todayStr(d: Date = Date()) = df.format(d)

    // ---------- Shortcuts ----------
    private fun prefs(ctx: Context): SharedPreferences = Preferences.prefs(ctx)

    // ---------- Gesetzliche Pausen ----------
    // > 6:00 netto -> 30 min, > 9:30 netto -> 45 min (30 + 15)
    private fun requiredLegalBreakMinutes(netMinutes: Long): Int = when {
        netMinutes > (9 * 60 + 30) -> 45
        netMinutes > 6 * 60        -> 30
        else -> 0
    }

    // ---------- Snapshot für UI/Widget ----------
    data class Snapshot(
        val runningWork: Boolean,
        val runningPause: Boolean,
        /** Brutto seit Start (ohne live-Abzüge), damit Rest während Pause „läuft“ */
        val sessionNettoMin: Long,
        val weekWorkedMin: Long,
        val weekTargetMin: Long,
        val dayNettoMin: Long,
        val dayExtraBreaksMin: Long,
        val legalRequiredMin: Int,
        /** Rest der Woche – darf negativ sein (Überstunden) */
        val weekRestMin: Long,
        val lastEntry: String?
    )

    // ---------- Rollover nur hier ----------
    fun ensureRolloverIfNeeded(ctx: Context) {
        val p = prefs(ctx)
        val last = p.getString(Preferences.LAST_DAY, null)
        val today = todayStr()
        if (last == null) {
            p.edit().putString(Preferences.LAST_DAY, today).apply()
            return
        }
        if (last == today) return

        // Tages-Keys des Vortages löschen, Woche bleibt unberührt
        p.edit()
            .remove(Preferences.START)
            .remove(Preferences.PSTART)
            .remove(Preferences.EXTRA_SESSION)
            .apply()

        p.edit()
            .remove(Preferences.k6hKey(last))
            .remove(Preferences.k9hKey(last))
            .remove(Preferences.kAppliedKey(last))
            .remove(Preferences.dayExtra(last))
            .remove(Preferences.dayNetto(last))
            .apply()

        try {
            Preferences.saveList(p, emptyList())
            Preferences.appendStempel(p, "Neuer Tag – Tagesliste zurückgesetzt")
        } catch (_: Throwable) {}

        p.edit().putString(Preferences.LAST_DAY, today).apply()
    }

    // ---------- Core Mutations ----------
    fun startArbeitszeit(ctx: Context) {
        val p = prefs(ctx)
        ensureRolloverIfNeeded(ctx)
        if (p.getLong(Preferences.START, 0L) > 0L) return
        val now = System.currentTimeMillis()
        p.edit()
            .putLong(Preferences.START, now)
            .putLong(Preferences.EXTRA_SESSION, 0L)
            .apply()
        showArbeitszeitNotification(ctx, now)
        StempeluhrWidget.updateWidget(ctx)
        Toast.makeText(ctx, "Arbeitszeit gestartet!", Toast.LENGTH_SHORT).show()
    }

    fun stopArbeitszeit(ctx: Context): String {
        val p = prefs(ctx)
        ensureRolloverIfNeeded(ctx)
        val start = p.getLong(Preferences.START, 0L)
        if (start == 0L) return "Keine laufende Arbeitszeit"

        val startDate = Date(start)
        val end = Date()
        val day = todayStr(end)

        // Falls noch Pause läuft: zunächst verbuchen
        val pStart = p.getLong(Preferences.PSTART, 0L)
        if (pStart > 0L) {
            val laufendePause = ((end.time - pStart) / 60000L).coerceAtLeast(0L)
            addExtraPauseSinceStart(p, laufendePause)
            addExtraPauseForDay(p, day, laufendePause)
            p.edit().remove(Preferences.PSTART).apply()
        }

        val brutto = ((end.time - start) / 60000L).coerceAtLeast(0L)
        val extraSess = p.getLong(Preferences.EXTRA_SESSION, 0L).coerceAtLeast(0L)
        val netto = (brutto - extraSess).coerceAtLeast(0L)

        // Tag/Woche fortschreiben (Netto = Leistung)
        addDayNetto(p, day, netto)
        addWeekWorked(p, netto)

        val text = "Arbeitszeit von ${tf.format(startDate)} bis ${tf.format(end)} (${formatHM(netto)})"
        Preferences.appendStempel(p, text)
        logEintrag(p, end, netto, "Arbeitszeit (${formatHM(netto)})")

        if (extraSess > 0) {
            val info = "Kurzpausen (Session): ${extraSess}m – werden nachgearbeitet"
            Preferences.appendStempel(p, info)
            logEintrag(p, end, extraSess, info)
        }

        p.edit()
            .remove(Preferences.START)
            .remove(Preferences.PSTART)
            .remove(Preferences.EXTRA_SESSION)
            .apply()

        cancelArbeitszeitNotification(ctx)
        StempeluhrWidget.updateWidget(ctx)
        Toast.makeText(ctx, "Feierabend!", Toast.LENGTH_SHORT).show()
        return text
    }

    fun startPause(ctx: Context) {
        val p = prefs(ctx)
        ensureRolloverIfNeeded(ctx)
        if (p.getLong(Preferences.PSTART, 0L) > 0L) return
        p.edit().putLong(Preferences.PSTART, System.currentTimeMillis()).apply()
        StempeluhrWidget.updateWidget(ctx)
        Toast.makeText(ctx, "Pause gestartet!", Toast.LENGTH_SHORT).show()
    }

    fun stopPause(ctx: Context): String {
        val p = prefs(ctx)
        ensureRolloverIfNeeded(ctx)
        val ps = p.getLong(Preferences.PSTART, 0L)
        if (ps == 0L) return "Keine laufende Pause"

        val start = Date(ps)
        val end = Date()
        val dauer = ((end.time - ps) / 60000L).coerceAtLeast(0L)
        val day = todayStr(end)

        // Kurzpause einmalig verbuchen -> Rest springt hoch
        addExtraPauseSinceStart(p, dauer)
        addExtraPauseForDay(p, day, dauer)

        val txt = "Pause von ${tf.format(start)} bis ${tf.format(end)} (${formatHM(dauer)})"
        Preferences.appendStempel(p, txt)
        logEintrag(p, end, dauer, "Pause (${formatHM(dauer)})")

        p.edit().remove(Preferences.PSTART).apply()
        StempeluhrWidget.updateWidget(ctx)
        Toast.makeText(ctx, txt, Toast.LENGTH_SHORT).show()
        return txt
    }

    // ---------- Snapshot fürs UI/Widget ----------
    fun snapshot(ctx: Context): Snapshot {
        ensureRolloverIfNeeded(ctx)
        val p = prefs(ctx)
        val day = todayStr()
        val runningWork = p.getLong(Preferences.START, 0L) > 0L
        val runningPause = p.getLong(Preferences.PSTART, 0L) > 0L

        // Brutto seit Start (ohne live-Abzüge)
        val sessionBrutto = computeSessionBruttoNow(p)
        val extraSess = p.getLong(Preferences.EXTRA_SESSION, 0L).coerceAtLeast(0L)

        val weekWorked = p.getLong(Preferences.WEEK_WORKED, 0L)
        val weekTarget = (p.getInt(Preferences.WEEK_TARGET, 40) * 60).toLong()
        val dayNetto = p.getLong(Preferences.dayNetto(day), 0L)
        val dayExtra = p.getLong(Preferences.dayExtra(day), 0L)

        val legalOn = p.getBoolean(Preferences.LAWS_ON, true)
        val legalToday = if (legalOn) {
            requiredLegalBreakMinutes(dayNetto + if (runningWork) sessionBrutto else 0L)
        } else 0

        val restRaw = if (runningWork) {
            weekTarget - (weekWorked + sessionBrutto) + legalToday + extraSess + dayExtra
        } else {
            weekTarget - weekWorked + legalToday + dayExtra
        }

        val list = try { Preferences.loadList(p) } catch (_: Throwable) { emptyList() }
        val lastEntry = list.lastOrNull()

        return Snapshot(
            runningWork = runningWork,
            runningPause = runningPause,
            sessionNettoMin = sessionBrutto,
            weekWorkedMin = weekWorked,
            weekTargetMin = weekTarget,
            dayNettoMin = dayNetto,
            dayExtraBreaksMin = dayExtra,
            legalRequiredMin = legalToday,
            weekRestMin = restRaw,
            lastEntry = lastEntry
        )
    }

    // ---------- Öffentliche Zusatzhelfer ----------

    /** Heutige Netto-Arbeitszeit (inkl. laufender Session, Pausen live abgezogen). */
    fun todayNettoLive(ctx: Context): Long {
        val p = prefs(ctx)
        val day = todayStr()

        // bereits verbuchtes Netto heute
        val base = p.getLong(Preferences.dayNetto(day), 0L)

        // keine laufende Session → nur base
        val start = p.getLong(Preferences.START, 0L)
        if (start == 0L) return base

        val now = System.currentTimeMillis()
        val seitStart = ((now - start) / 60000L).coerceAtLeast(0L)

        // Kurzpausen, die in dieser Session angefallen sind (werden nachgearbeitet)
        val extraSess = p.getLong(Preferences.EXTRA_SESSION, 0L).coerceAtLeast(0L)

        // aktuell laufende Pause (falls aktiv)
        val pStart = p.getLong(Preferences.PSTART, 0L)
        val laufendePause = if (pStart > 0L) ((now - pStart) / 60000L).coerceAtLeast(0L) else 0L

        val sessionNettoLive = (seitStart - extraSess - laufendePause).coerceAtLeast(0L)
        return (base + sessionNettoLive).coerceAtLeast(0L)
    }

    fun legalMinutesForToday(ctx: Context, includeRunning: Boolean = true): Int {
        val p = prefs(ctx)
        val day = todayStr()
        val legalOn = p.getBoolean(Preferences.LAWS_ON, true)
        if (!legalOn) return 0

        val running = p.getLong(Preferences.START, 0L) > 0L
        val sessionBrutto = if (includeRunning && running) {
            ((System.currentTimeMillis() - p.getLong(Preferences.START, 0L)) / 60000L).coerceAtLeast(0L)
        } else 0L
        val dayNetto = p.getLong(Preferences.dayNetto(day), 0L)
        return requiredLegalBreakMinutes(dayNetto + sessionBrutto)
    }

    fun legalInfoLabel(ctx: Context): String = when (val cur = legalMinutesForToday(ctx, true)) {
        45   -> "45 min gesetzliche Pause eingerechnet (30 + 15)"
        30   -> "30 min gesetzliche Pause eingerechnet"
        else -> "Keine gesetzliche Pause fällig"
    }

    fun computeWeekRestNow(p: SharedPreferences, laufendeSessionBruttoMin: Long = 0L): Long {
        val running = p.getLong(Preferences.START, 0L) > 0L
        val weekTarget = (p.getInt(Preferences.WEEK_TARGET, 40) * 60).toLong()
        val weekWorked = p.getLong(Preferences.WEEK_WORKED, 0L)
        val extraSess = p.getLong(Preferences.EXTRA_SESSION, 0L).coerceAtLeast(0L)

        val day = todayStr()
        val dayNetto = p.getLong(Preferences.dayNetto(day), 0L)
        val legalOn = p.getBoolean(Preferences.LAWS_ON, true)
        val legalToday = if (legalOn) {
            requiredLegalBreakMinutes(dayNetto + if (running) laufendeSessionBruttoMin else 0L)
        } else 0

        return if (running) {
            weekTarget - (weekWorked + laufendeSessionBruttoMin) + legalToday + extraSess
        } else {
            weekTarget - weekWorked + legalToday
        }
    }

    // ---------- NEU: Heutigen Tag sauber zurücksetzen ----------
    fun resetToday(ctx: Context, rollbackWeek: Boolean = true) {
        val p = prefs(ctx)
        ensureRolloverIfNeeded(ctx)

        // Laufendes stoppen, ohne etwas zu verbuchen
        val hadRunningWork = p.getLong(Preferences.START, 0L) > 0L
        val hadRunningPause = p.getLong(Preferences.PSTART, 0L) > 0L
        if (hadRunningWork || hadRunningPause) {
            p.edit()
                .remove(Preferences.START)
                .remove(Preferences.PSTART)
                .remove(Preferences.EXTRA_SESSION)
                .apply()
            cancelArbeitszeitNotification(ctx)
        }

        val today = todayStr()
        val dayNettoKey = Preferences.dayNetto(today)
        val dayExtraKey = Preferences.dayExtra(today)
        val dayNetto = p.getLong(dayNettoKey, 0L)

        // Wochenleistung um heutiges Netto zurückdrehen (optional)
        if (rollbackWeek && dayNetto != 0L) {
            val weekWorked = p.getLong(Preferences.WEEK_WORKED, 0L)
            p.edit().putLong(Preferences.WEEK_WORKED, (weekWorked - dayNetto)).apply()
        }

        // Heutige Tageswerte löschen
        p.edit()
            .remove(dayNettoKey)
            .remove(dayExtraKey)
            .remove(Preferences.k6hKey(today))
            .remove(Preferences.k9hKey(today))
            .remove(Preferences.kAppliedKey(today))
            .apply()

        // Liste leeren + Info
        Preferences.saveList(p, emptyList())
        Preferences.appendStempel(p, "Tag zurückgesetzt – heutige Einträge entfernt")

        StempeluhrWidget.updateWidget(ctx)
        Toast.makeText(ctx, "Tag zurückgesetzt", Toast.LENGTH_SHORT).show()
    }

    // ---------- Helpers ----------
    private fun computeSessionBruttoNow(p: SharedPreferences): Long {
        val start = p.getLong(Preferences.START, 0L)
        if (start == 0L) return 0L
        return ((System.currentTimeMillis() - start) / 60000L).coerceAtLeast(0L)
    }

    private fun addExtraPauseSinceStart(p: SharedPreferences, m: Long) {
        val cur = p.getLong(Preferences.EXTRA_SESSION, 0L)
        p.edit().putLong(Preferences.EXTRA_SESSION, (cur + m).coerceAtLeast(0L)).apply()
    }
    private fun addExtraPauseForDay(p: SharedPreferences, day: String, m: Long) {
        val cur = p.getLong(Preferences.dayExtra(day), 0L)
        p.edit().putLong(Preferences.dayExtra(day), (cur + m).coerceAtLeast(0L)).apply()
    }
    private fun addDayNetto(p: SharedPreferences, day: String, m: Long) {
        val cur = p.getLong(Preferences.dayNetto(day), 0L)
        p.edit().putLong(Preferences.dayNetto(day), (cur + m).coerceAtLeast(0L)).apply()
    }
    private fun addWeekWorked(p: SharedPreferences, m: Long) {
        val cur = p.getLong(Preferences.WEEK_WORKED, 0L)
        p.edit().putLong(Preferences.WEEK_WORKED, (cur + m)).apply()
    }

    // ---------- Interner Logger ----------
    private const val KEY_ARBEITS_LOGS = "arbeitsLogs"

    private fun logEintrag(
        prefs: SharedPreferences,
        date: Date,
        minuten: Long,
        text: String
    ) {
        val logs = prefs.getStringSet(KEY_ARBEITS_LOGS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val dayStr = df.format(date)
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val weekday = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY    -> "Mo"
            java.util.Calendar.TUESDAY   -> "Di"
            java.util.Calendar.WEDNESDAY -> "Mi"
            java.util.Calendar.THURSDAY  -> "Do"
            java.util.Calendar.FRIDAY    -> "Fr"
            java.util.Calendar.SATURDAY  -> "Sa"
            else                         -> "So"
        }
        logs.add("$dayStr|$weekday|$minuten|$text")
        prefs.edit().putStringSet(KEY_ARBEITS_LOGS, logs).apply()
    }

    // ---------- Formatter ----------
    @JvmStatic
    fun formatHM(totalMin: Long): String {
        val sign = if (totalMin < 0) "-" else ""
        val abs = kotlin.math.abs(totalMin)
        val h = abs / 60
        val m = abs % 60
        return String.format("%s%dh %02dm", sign, h, m)
    }
}
