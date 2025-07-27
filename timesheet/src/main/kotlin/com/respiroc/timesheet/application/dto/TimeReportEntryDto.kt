package com.respiroc.timesheet.application.dto

import java.time.LocalDate

data class TimeReportEntryDto(
    val date: LocalDate,
    val projectName: String,
    val activityName: String?,
    val task: String?, // Combined activity + description
    val hours: Double,
    val notes: String?
)
