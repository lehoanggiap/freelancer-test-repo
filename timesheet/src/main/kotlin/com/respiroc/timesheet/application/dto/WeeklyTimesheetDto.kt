package com.respiroc.timesheet.application.dto

data class WeeklyTimesheetDto(
    val weekStart: String, // ISO date format
    val weekEnd: String,
    val weekNumber: Int,
    val year: Int,
    val rows: List<TimesheetRowDto>,
    val dayTotals: Map<String, Double>,
    val grandTotal: Double,
    val isSubmitted: Boolean = false,
    val approvalStatus: String? = null, // PENDING, APPROVED, REJECTED
    val submittedAt: String? = null,
    val approvedAt: String? = null,
    val approvedBy: String? = null,
    val rejectionReason: String? = null
)
