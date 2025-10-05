package com.example.stempeluhr.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    val TF = SimpleDateFormat("HH:mm", Locale.getDefault())
    val DF = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun weekOfYear(date: Date = Date()): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { time = date }
        return cal.get(Calendar.WEEK_OF_YEAR) to cal.get(Calendar.YEAR)
    }
}


