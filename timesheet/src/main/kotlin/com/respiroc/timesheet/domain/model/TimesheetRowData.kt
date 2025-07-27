package com.respiroc.timesheet.domain.model

data class TimesheetRowData(
    val hours: MutableMap<String, Double>,
    val comments: MutableMap<String, String?>,
    val entryIds: MutableMap<String, Int?>
)
