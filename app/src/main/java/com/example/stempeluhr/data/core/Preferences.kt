package com.example.stempeluhr.data.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zentrale Keys + kleine Helfer rund um SharedPreferences und die sichtbare Stempelliste.
 * Achtung: Keine Business-Logik hier – nur Utilities/Keys.
 */
object Preferences {

    // ---------- SharedPreferences-Datei ----------
    const val PREFS = "stempeluhr"

    // ---------- Laufende Session ----------
    const val START = "arbeitsStartMillis"
    const val PSTART = "pauseStartMillis"
    const val EXTRA_SESSION = "extraPauseSinceStartMin"   // Summe Kurzpausen der laufenden Session

    // ---------- Woche ----------
    const val WEEK_WORKED = "gearbeiteteMinuten"          // Wochen-Netto (Leistung, in Minuten)
    const val WEEK_TARGET = "wochenArbeitszeit"           // Wochen-Soll (Stunden, Int)

    // ---------- Flags ----------
    const val LAST_DAY = "lastDay"                        // yyyy-MM-dd des letzten App-Tags
    const val LAWS_ON = "pausenGesetzAktiv"               // gesetzliche Pausen berücksichtigen?

    // ---------- Tagesbezogene Keys (dynamisch) ----------
    fun dayNetto(day: String) = "arbeitsNettoMin_$day"    // Netto je Kalendertag
    fun dayExtra(day: String) = "extraPauseMin_$day"      // Kurzpausen je Kalendertag

    // ---------- (Legacy) Pflichtpausen-Flags (nur Aufräumen bei Tageswechsel) ----------
    fun k6hKey(day: String) = "gesetzlichePause6h_$day"
    fun k9hKey(day: String) = "gesetzlichePause9h_$day"
    fun kAppliedKey(day: String) = "gesetzlicheBereitsAbgezogen_$day"

    // ---------- Stempelliste (für UI/Widget) ----------
    private const val KEY_STEMPEL_LIST_JSON = "stempelListe_json"
    private const val KEY_STEMPEL_LIST_SET  = "stempelListe" // legacy-Format (StringSet)

    /** Speichert die sichtbare Stempelliste atomar als JSON-Array. */
    fun saveList(prefs: SharedPreferences, list: List<String>) {
        prefs.edit()
            .putString(KEY_STEMPEL_LIST_JSON, JSONArray(list).toString())
            .apply()
    }

    /**
     * Lädt die sichtbare Stempelliste.
     * Migriert automatisch vom alten StringSet-Format.
     */
    fun loadList(prefs: SharedPreferences): List<String> {
        // Neues JSON-Format?
        prefs.getString(KEY_STEMPEL_LIST_JSON, null)?.let { json ->
            return try {
                val arr = JSONArray(json)
                List(arr.length()) { i -> arr.getString(i) }
            } catch (_: Exception) {
                emptyList()
            }
        }
        // Legacy-Format migrieren (falls noch vorhanden)
        prefs.getStringSet(KEY_STEMPEL_LIST_SET, null)?.let { legacy ->
            val migrated = legacy.toList()
            saveList(prefs, migrated)
            prefs.edit().remove(KEY_STEMPEL_LIST_SET).apply()
            return migrated
        }
        return emptyList()
    }

    /** Bequemer Append-Helper, der race-safe die Liste erweitert. */
    fun appendStempel(prefs: SharedPreferences, text: String) {
        val cur = loadList(prefs).toMutableList()
        cur.add(text)
        saveList(prefs, cur)
    }

    /** Nur die sichtbare Tagesliste leeren. Wochenwerte bleiben unberührt. */
    fun clearDayList(prefs: SharedPreferences) {
        saveList(prefs, emptyList())
    }

    // ---------- Datumshilfe ----------
    private val DF_DAY = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun todayStr(): String = DF_DAY.format(Date())

    /**
     * Tages-Reset:
     * - sichtbare Stempelliste leert sich
     * - laufende Start-/Pause-/Session-Kurzpausen Marker werden entfernt
     * - dayNetto/dayExtra des **Vortags** werden gelöscht (Woche bleibt!)
     * - lastDay wird auf "heute" gesetzt
     *
     * CRASH-FIX: Diese Funktion erwartet **SharedPreferences**, nicht den Context!
     */
    fun resetToday(prefs: SharedPreferences) {
        val today = todayStr()
        val last = prefs.getString(LAST_DAY, null)

        // laufende Marker (falls gesetzt) entfernen
        prefs.edit()
            .remove(START)
            .remove(PSTART)
            .remove(EXTRA_SESSION)
            .apply()

        // tagesbezogene Schlüssel des Vortags aufräumen
        last?.let { d ->
            prefs.edit()
                .remove(k6hKey(d))
                .remove(k9hKey(d))
                .remove(kAppliedKey(d))
                .remove(dayNetto(d))
                .remove(dayExtra(d))
                .apply()
        }

        // Liste leeren & Hinweis setzen
        clearDayList(prefs)
        appendStempel(prefs, "Neuer Tag – Tagesliste zurückgesetzt")

        // neuen Tag markieren
        prefs.edit().putString(LAST_DAY, today).apply()
    }

    // ---------- Convenience ----------
    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
