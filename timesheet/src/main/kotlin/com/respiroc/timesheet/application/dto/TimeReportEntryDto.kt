package com.respiroc.timesheet.application.dto

data class TimeReportEntryDto(
    val date: String,
    val projectName: String,
    val activityName: String?,
    val task: String?, // Combined activity + description
    val hours: Double,
    val notes: String?
)
