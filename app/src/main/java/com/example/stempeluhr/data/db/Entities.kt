package com.example.stempeluhr.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_session")
data class WorkSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long,
    val nettoMin: Long,
    val day: String,         // yyyy-MM-dd
    val weekOfYear: Int,
    val weekYear: Int
)

@Entity(tableName = "break_entry")
data class BreakEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long,
    val minutes: Long,
    val day: String,
    val weekOfYear: Int,
    val weekYear: Int
)

@Entity(tableName = "stamp_log")
data class StampLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val whenMillis: Long,
    val minutes: Long,     // für Arbeitszeit-Logs = Netto; für Pausen = Pausen-Minuten
    val text: String,
    val day: String,
    val weekOfYear: Int,
    val weekYear: Int
)
