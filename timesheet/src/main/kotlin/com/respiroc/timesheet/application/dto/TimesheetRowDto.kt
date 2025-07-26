package com.respiroc.timesheet.application.dto

data class TimesheetRowDto(
    val rowId: Int,
    val projectId: Int?,
    val activityId: Int?,
    val hours: Map<String, Double>, // day -> hours
    val comments: Map<String, String?>, // day -> comment
    val entryIds: Map<String, Int?> = emptyMap() // day -> timesheet_entry_id (null if not persisted)
) {
    val totalHours: Double
        get() = hours.values.sum()
        
    val hasSavedEntries: Boolean
        get() = entryIds.values.any { it != null }
}
