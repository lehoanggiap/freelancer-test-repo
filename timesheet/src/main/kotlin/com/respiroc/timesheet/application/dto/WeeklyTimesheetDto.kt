package com.respiroc.timesheet.application.dto

import java.time.LocalDate

data class WeeklyTimesheetDto(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
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
