package com.respiroc.timesheet.application.dto

data class MonthlyReportEntryDto(
    val date: String,
    val employeeName: String,
    val hoursWorked: Double,
    val projectName: String,
    val task: String,
    val notes: String?,
    val totalHoursLogged: Double,
    val approvedEntries: Int,
    val pendingApprovals: Int
)
