package com.respiroc.webapp.controller.request

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class ApproveTimesheetRequest(
    @field:NotNull(message = "Month is required")
    val month: LocalDate,
    
    val employeeId: Int? = null,
    
    val projectId: Int? = null,
    
    val status: String? = null
)
