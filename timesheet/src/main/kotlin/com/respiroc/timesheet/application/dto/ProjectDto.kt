package com.respiroc.timesheet.application.dto

data class ProjectDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true
)
