package com.example.stempeluhr.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stempeluhr.data.core.Preferences
import com.example.stempeluhr.widget.StempeluhrWidget
import com.example.stempeluhr.data.core.ArbeitszeitManager
import com.example.stempeluhr.util.cancelArbeitszeitNotification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBackClick: () -> Unit
) {
    // ⬇️ EINMAL die Prefs holen – wichtig für den Crash-Fix
    val prefs = Preferences.prefs(context)

    var wochenArbeitszeit = remember { mutableStateOf(prefs.getInt(Preferences.WEEK_TARGET, 40).toString()) }
    var pausenGesetzAktiv = remember { mutableStateOf(prefs.getBoolean(Preferences.LAWS_ON, true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Wochen-Soll
            OutlinedTextField(
                value = wochenArbeitszeit.value,
                onValueChange = { v ->
                    wochenArbeitszeit.value = v
                    val hours = v.toIntOrNull()?.coerceIn(0, 80) ?: 40
                    prefs.edit().putInt(Preferences.WEEK_TARGET, hours).apply()
                    StempeluhrWidget.updateWidget(context)
                },
                label = { Text("Wochenarbeitszeit (Stunden)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Gesetzliche Pausen
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = pausenGesetzAktiv.value,
                    onCheckedChange = { checked ->
                        pausenGesetzAktiv.value = checked
                        prefs.edit().putBoolean(Preferences.LAWS_ON, checked).apply()
                        StempeluhrWidget.updateWidget(context)
                    }
                )
                Text("Pausen nach deutschem Gesetz berücksichtigen")
            }

            Divider()
            Spacer(Modifier.height(8.dp))

            // 🔄 Tagesliste zurücksetzen (CRASH-FIX: prefs statt context übergeben!)
            Button(
                onClick = {
                    ArbeitszeitManager.resetToday(context, rollbackWeek = true)

                    Toast.makeText(context, "Tag zurückgesetzt!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Tagesliste zurücksetzen")
            }
        }
    }
}
