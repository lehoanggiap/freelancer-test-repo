package com.respiroc.timesheet.application.dto

data class MonthlyReportDto(
    val month: String,
    val year: Int,
    val entries: List<MonthlyReportEntryDto>,
    val totalHours: Double,
    val totalEntries: Int,
    val approvedEntries: Int,
    val pendingApprovals: Int
)
