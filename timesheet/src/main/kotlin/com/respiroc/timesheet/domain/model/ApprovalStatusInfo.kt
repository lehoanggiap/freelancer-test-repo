package com.respiroc.timesheet.domain.model

data class ApprovalStatusInfo(
    val isSubmitted: Boolean,
    val status: String?,
    val submittedAt: String?,
    val approvedAt: String?
)
