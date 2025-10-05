package com.example.stempeluhr.data.repo

import android.content.Context
import com.example.stempeluhr.data.db.*

class TimeRepository private constructor(private val db: AppDatabase) {

    suspend fun insertWork(s: WorkSession) = db.workDao().insert(s)
    suspend fun insertBreak(b: BreakEntry) = db.breakDao().insert(b)
    suspend fun insertLog(l: StampLog) = db.logDao().insert(l)

    suspend fun getWeekLogs(year: Int, week: Int) = db.logDao().forWeek(year, week)
    suspend fun getWeekSessions(year: Int, week: Int) = db.workDao().forWeek(year, week)

    companion object {
        @Volatile private var INSTANCE: TimeRepository? = null
        fun get(context: Context): TimeRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimeRepository(AppDatabase.get(context)).also { INSTANCE = it }
            }
    }
}
