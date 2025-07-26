package com.respiroc.webapp.controller.request

import com.respiroc.timesheet.application.dto.TimesheetRowDto
import jakarta.validation.Valid
import jakarta.validation.constraints.*

data class TimesheetRowRequest(
    val rowId: Int,
    
    @field:NotNull(message = "Project must be selected")
    val projectId: Int?,
    
    @field:NotNull(message = "Activity must be selected") 
    val activityId: Int?,
    
    @field:Valid
    val hours: Map<String, @DecimalMin(value = "0.0", message = "Hours must be positive") @DecimalMax(value = "24.0", message = "Hours cannot exceed 24") Double>,
    
    val comments: Map<String, String?>,
    val entryIds: Map<String, Int?> = emptyMap()
) {
    fun toDto(): TimesheetRowDto {
        return TimesheetRowDto(
            rowId = rowId,
            projectId = projectId,
            activityId = activityId,
            hours = hours,
            comments = comments,
            entryIds = entryIds
        )
    }
}
