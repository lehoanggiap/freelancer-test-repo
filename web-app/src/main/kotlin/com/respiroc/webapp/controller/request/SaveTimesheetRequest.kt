package com.respiroc.webapp.controller.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

data class SaveTimesheetRequest(
    @field:NotNull(message = "Week start date is required")
    val weekStart: LocalDate,
    
    @field:Valid
    @field:NotEmpty(message = "At least one timesheet row is required")
    val rows: List<TimesheetRowRequest>
) {
    fun toTimesheetRowDtos() = rows.map { it.toPayload() }
}
