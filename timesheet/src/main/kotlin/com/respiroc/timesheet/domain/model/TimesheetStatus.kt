package com.respiroc.timesheet.domain.model

enum class TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    IN_PROGRESS;
    
    fun isApproved(): Boolean {
        return this == APPROVED
    }
}