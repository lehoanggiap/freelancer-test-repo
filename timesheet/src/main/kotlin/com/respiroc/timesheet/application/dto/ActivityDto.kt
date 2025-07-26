package com.respiroc.timesheet.application.dto

data class ActivityDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val projectId: Int? = null,
    val isBillable: Boolean = true,
    val hourlyRate: Double? = null,
    val isActive: Boolean = true
)
