package com.respiroc.timesheet.application.payload

import java.time.LocalDate

data class TimeReportEntryPayload(
    val date: LocalDate,
    val projectName: String,
    val activityName: String?,
    val task: String?, // Combined activity + description
    val hours: Double,
    val notes: String?
)
