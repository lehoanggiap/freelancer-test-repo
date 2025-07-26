package com.respiroc.webapp.controller.request

import jakarta.validation.constraints.NotBlank

data class NewBankAccountRequest(
    @field:NotBlank(message = "Country is required")
    val countryCode: String = "",

    @field:NotBlank(message = "Account number is required")
    val bban: String = ""
    )
