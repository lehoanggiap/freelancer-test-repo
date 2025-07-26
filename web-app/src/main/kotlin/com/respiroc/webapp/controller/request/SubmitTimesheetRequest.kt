package com.respiroc.webapp.controller.rest.request

import java.time.LocalDate

data class SubmitTimesheetRequest(
    val weekStart: LocalDate
)
