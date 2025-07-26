package com.respiroc.timesheet.domain.model

enum class TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PENDING;
    
    fun isApproved(): Boolean {
        return this == APPROVED
    }
}