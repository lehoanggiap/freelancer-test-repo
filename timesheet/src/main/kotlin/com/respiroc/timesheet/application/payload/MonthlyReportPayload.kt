package com.respiroc.timesheet.application.payload

data class MonthlyReportPayload(
    val month: String,
    val year: Int,
    val entries: List<MonthlyReportEntryPayload>,
    val totalHours: Double,
    val totalEntries: Int,
    val approvedEntries: Int,
    val pendingApprovals: Int
)
