package com.respiroc.timesheet.application.dto

import java.time.LocalDate

data class MonthlyReportEntryDto(
    val date: LocalDate,
    val employeeName: String,
    val hoursWorked: Double,
    val projectName: String,
    val task: String,
    val notes: String?,
    val status: String,
    val totalHoursLogged: Double,
    val approvedEntries: Int,
    val pendingApprovals: Int
)
