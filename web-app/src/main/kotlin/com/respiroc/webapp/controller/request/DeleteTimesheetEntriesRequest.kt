package com.respiroc.webapp.controller.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

data class DeleteTimesheetEntriesRequest(
    @field:NotEmpty(message = "At least one entry ID is required")
    val entryIds: List<Int>,

    @field:NotNull(message = "Week start date is required")
    val weekStart: LocalDate
)
