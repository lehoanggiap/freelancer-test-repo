package com.respiroc.bank.application.payload

data class NewBankAccountPayload(
    var countryCode: String,
    var bban: String
)
