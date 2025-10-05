package com.example.stempeluhr.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stempeluhr.data.core.ArbeitszeitManager
import com.example.stempeluhr.data.core.Preferences
import com.example.stempeluhr.widget.StempeluhrWidget
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StempelUhrScreen(
    context: Context,
    onSettingsClick: () -> Unit,
    onAuswertungClick: () -> Unit
) {
    val prefs = context.getSharedPreferences(Preferences.PREFS, Context.MODE_PRIVATE)

    // Tageswechsel ausschließlich über den Manager
    LaunchedEffect(Unit) { ArbeitszeitManager.ensureRolloverIfNeeded(context) }

    // UI-State
    var stempelListe by remember { mutableStateOf(Preferences.loadList(prefs)) }
    var editingEntry by remember { mutableStateOf<String?>(null) }
    var newStart by remember { mutableStateOf("") }
    var newEnd by remember { mutableStateOf("") }

    // Refresh-Trigger
    var refresh by remember { mutableStateOf(0) }

    // Live-Ticker (1x/Minute) – holt externe Änderungen (z.B. Widget) rein
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            ArbeitszeitManager.ensureRolloverIfNeeded(context)
            stempelListe = Preferences.loadList(prefs)
            tick++
        }
    }

    // Snapshot
    val snap = remember(tick, refresh) { ArbeitszeitManager.snapshot(context) }

    // Header-Datum
    val now = remember(tick) { Date() }
    val wochentagRaw = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
    val wochentag = remember(wochentagRaw) {
        wochentagRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val datum = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
    val kw = remember(tick) { Calendar.getInstance().apply { time = now }.get(Calendar.WEEK_OF_YEAR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stempeluhr") },
                actions = {
                    IconButton(onClick = { onSettingsClick() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onAuswertungClick() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Auswertung ansehen") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header
            Text(
                text = "$wochentag, $datum • KW $kw",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))

            // Restzeit (kann negativ sein)
            Text(
                text = "Verbleibende Arbeitszeit: ${ArbeitszeitManager.formatHM(snap.weekRestMin)}",
                style = MaterialTheme.typography.titleLarge
            )

            // Live: heute gearbeitete Nettozeit
            val heuteNettoMin = remember(tick, refresh) { ArbeitszeitManager.todayNettoLive(context) }

            Text(
                text = "Heute gearbeitet: ${ArbeitszeitManager.formatHM(heuteNettoMin)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Gesetzliche-Pause-Hinweis
            Spacer(Modifier.height(6.dp))
            AssistChip(
                onClick = { /* nur Info */ },
                label = { Text(ArbeitszeitManager.legalInfoLabel(context)) }
            )

            Spacer(Modifier.height(12.dp))

            // Start/Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        ArbeitszeitManager.startArbeitszeit(context)
                        stempelListe = Preferences.loadList(prefs)
                        StempeluhrWidget.updateWidget(context)
                        refresh++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Arbeitszeit starten") }

                Button(
                    onClick = {
                        val text = ArbeitszeitManager.stopArbeitszeit(context)
                        if (text.isNotBlank()) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        stempelListe = Preferences.loadList(prefs)
                        StempeluhrWidget.updateWidget(context)
                        refresh++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Feierabend") }
            }

            Spacer(Modifier.height(8.dp))

            // Pause
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        ArbeitszeitManager.startPause(context)
                        stempelListe = Preferences.loadList(prefs)
                        StempeluhrWidget.updateWidget(context)
                        refresh++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Pause starten") }

                Button(
                    onClick = {
                        val text = ArbeitszeitManager.stopPause(context)
                        if (text.isNotBlank()) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        stempelListe = Preferences.loadList(prefs)
                        StempeluhrWidget.updateWidget(context)
                        refresh++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Pause beenden") }
            }

            Spacer(Modifier.height(16.dp))
            Text("Stempelzeiten", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Liste
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val itemsList = stempelListe.asReversed()
                itemsIndexed(
                    items = itemsList,
                    key = { idx, item -> item.hashCode() + idx }
                ) { _, eintrag ->
                    key(eintrag) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    eintrag.startsWith("Arbeitszeit") -> MaterialTheme.colorScheme.primaryContainer
                                    eintrag.startsWith("Pause")       -> MaterialTheme.colorScheme.tertiaryContainer
                                    else                              -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            eintrag.startsWith("Arbeitszeit") -> "Arbeitszeit"
                                            eintrag.startsWith("Pause")       -> "Pause"
                                            else                              -> "Info"
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(text = eintrag, fontSize = 12.sp)
                                }

                                if (!eintrag.startsWith("Gesetzliche Pause")) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = {
                                            editingEntry = eintrag
                                            val match = Regex("von (\\d{2}:\\d{2}) bis (\\d{2}:\\d{2})")
                                                .find(eintrag)
                                            newStart = match?.groupValues?.get(1) ?: ""
                                            newEnd = match?.groupValues?.get(2) ?: ""
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Eintrag bearbeiten")
                                        }
                                        IconButton(onClick = {
                                            deleteEntryAndFixTotals(prefs, eintrag)
                                            val updated = Preferences
                                                .loadList(prefs)
                                                .toMutableList().apply { remove(eintrag) }
                                            Preferences.saveList(prefs, updated)
                                            stempelListe = updated

                                            StempeluhrWidget.updateWidget(context)
                                            Toast.makeText(context, "Eintrag gelöscht", Toast.LENGTH_SHORT).show()
                                            refresh++
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Eintrag löschen")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bearbeiten-Dialog
    if (editingEntry != null && !editingEntry!!.startsWith("Gesetzliche Pause")) {
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Eintrag bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newStart,
                        onValueChange = { newStart = it },
                        label = { Text("Start (HH:mm)") }
                    )
                    OutlinedTextField(
                        value = newEnd,
                        onValueChange = { newEnd = it },
                        label = { Text("Ende (HH:mm)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val s = fmt.parse(newStart)
                        val e = fmt.parse(newEnd)

                        if (s == null || e == null) {
                            Toast.makeText(context, "Ungültiges Format (HH:mm)", Toast.LENGTH_SHORT).show()
                        } else if (e.before(s)) {
                            Toast.makeText(context, "Endzeit darf nicht vor Startzeit liegen!", Toast.LENGTH_SHORT).show()
                        } else {
                            val neueDauerMin = ((e.time - s.time) / 60000) // Netto-Minuten für Eintrag

                            // Alte Dauer aus Text
                            val durRe = Regex("\\((\\d+)h (\\d+)m\\)")
                            val match = durRe.find(editingEntry!!)
                            val alteDauerMin = if (match != null)
                                (match.groupValues[1].toLong() * 60) + match.groupValues[2].toLong()
                            else 0L

                            if (editingEntry!!.startsWith("Arbeitszeit")) {
                                val diff = neueDauerMin - alteDauerMin

                                // Wochenstand korrigieren (darf negativ werden)
                                val weekWorked = prefs.getLong(Preferences.WEEK_WORKED, 0L)
                                prefs.edit().putLong(Preferences.WEEK_WORKED, weekWorked + diff).apply()

                                // Tages-Netto korrigieren (auf HEUTE)
                                val todayKey = Preferences.dayNetto(
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                )
                                val dayNettoAlt = prefs.getLong(todayKey, 0L)
                                prefs.edit().putLong(todayKey, (dayNettoAlt + diff).coerceAtLeast(0L)).apply()

                                // Sichtbarer Eintrag ändern
                                val neuerText =
                                    "Arbeitszeit von $newStart bis $newEnd (${neueDauerMin / 60}h ${neueDauerMin % 60}m)"
                                val updated = Preferences.loadList(prefs).map {
                                    if (it == editingEntry) neuerText else it
                                }
                                Preferences.saveList(prefs, updated)
                            } else {
                                // Pause → Tages-Kurzpausen anpassen
                                val delta = (neueDauerMin - alteDauerMin)
                                val todayKey = Preferences.dayExtra(
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                )
                                val dayExtraAlt = prefs.getLong(todayKey, 0L)
                                prefs.edit().putLong(todayKey, (dayExtraAlt + delta).coerceAtLeast(0L)).apply()

                                val dauerText = "${neueDauerMin / 60}h ${neueDauerMin % 60}m"
                                val updated = Preferences.loadList(prefs).map {
                                    if (it == editingEntry) "Pause von $newStart bis $newEnd ($dauerText)" else it
                                }
                                Preferences.saveList(prefs, updated)
                            }

                            stempelListe = Preferences.loadList(prefs)
                            StempeluhrWidget.updateWidget(context)
                            Toast.makeText(context, "Eintrag aktualisiert", Toast.LENGTH_SHORT).show()
                            refresh++
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "Fehler beim Speichern", Toast.LENGTH_SHORT).show()
                    }
                    editingEntry = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                Button(onClick = { editingEntry = null }) { Text("Abbrechen") }
            }
        )
    }
}

/**
 * Löscht einen Eintrag aus der Liste **und** korrigiert die Summen.
 * - "Arbeitszeit … (Xh Ym)" → WEEK_WORKED -= minutes, dayNetto -= minutes
 * - "Pause … (Xh Ym)"       → dayExtra   -= minutes
 */
private fun deleteEntryAndFixTotals(
    prefs: android.content.SharedPreferences,
    entry: String
) {
    val durRe = Regex("\\((\\d+)h (\\d+)m\\)")
    val m = durRe.find(entry)
    val minutes = if (m != null)
        (m.groupValues[1].toLong() * 60) + m.groupValues[2].toLong()
    else 0L

    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    if (entry.startsWith("Arbeitszeit") && minutes > 0L) {
        val weekWorked = prefs.getLong(Preferences.WEEK_WORKED, 0L)
        prefs.edit().putLong(Preferences.WEEK_WORKED, (weekWorked - minutes)).apply()

        val dayKey = Preferences.dayNetto(todayStr)
        val dayNettoAlt = prefs.getLong(dayKey, 0L)
        prefs.edit().putLong(dayKey, (dayNettoAlt - minutes).coerceAtLeast(0L)).apply()
    } else if (entry.startsWith("Pause") && minutes > 0L) {
        val dayKey = Preferences.dayExtra(todayStr)
        val dayExtraAlt = prefs.getLong(dayKey, 0L)
        prefs.edit().putLong(dayKey, (dayExtraAlt - minutes).coerceAtLeast(0L)).apply()
    }
}
