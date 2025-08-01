package com.respiroc.timesheet.application.payload

import java.time.LocalDate

data class MonthlyReportEntryPayload(
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
