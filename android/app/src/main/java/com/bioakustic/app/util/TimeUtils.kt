package com.bioakustic.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val DATE_TIME = SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale("cs", "CZ"))
    private val DATE_TIME_SHORT = SimpleDateFormat("d. M. HH:mm", Locale("cs", "CZ"))
    val EDIT_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun formatDateTime(epochMs: Long): String = DATE_TIME.format(Date(epochMs))

    fun formatShort(epochMs: Long): String = DATE_TIME_SHORT.format(Date(epochMs))

    fun formatForEdit(epochMs: Long): String = EDIT_FORMAT.format(Date(epochMs))

    /** Parses "yyyy-MM-dd HH:mm", null when invalid. */
    fun parseEdited(text: String): Long? {
        return try {
            EDIT_FORMAT.isLenient = false
            EDIT_FORMAT.parse(text.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    fun formatPercent(confidence: Float?): String =
        if (confidence == null) "–" else "%d %%".format((confidence * 100).toInt())
}
