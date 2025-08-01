package com.respiroc.timesheet.application.payload

data class ProjectPayload(
    val id: Int,
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true
)
