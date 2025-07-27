package com.respiroc.timesheet.domain.model

data class MonthlyStatistics(
    val totalHours: Double,
    val totalEntries: Int,
    val approvedEntries: Int,
    val pendingApprovals: Int
)
