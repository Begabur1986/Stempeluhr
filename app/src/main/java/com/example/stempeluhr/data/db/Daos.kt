package com.example.stempeluhr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WorkDao {
    @Insert
    suspend fun insert(session: WorkSession): Long

    @Query("SELECT * FROM work_session WHERE weekYear = :year AND weekOfYear = :week ORDER BY startMillis ASC")
    suspend fun forWeek(year: Int, week: Int): List<WorkSession>
}

@Dao
interface BreakDao {
    @Insert
    suspend fun insert(entry: BreakEntry): Long

    @Query("SELECT * FROM break_entry WHERE day = :day ORDER BY startMillis ASC")
    suspend fun forDay(day: String): List<BreakEntry>
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: StampLog): Long

    @Query("SELECT * FROM stamp_log WHERE weekYear = :year AND weekOfYear = :week ORDER BY whenMillis ASC")
    suspend fun forWeek(year: Int, week: Int): List<StampLog>
}


