package com.respiroc.bank.application

sealed class BBANParser {
    abstract val countryCode: String
    abstract fun extractBankCode(bban: String): String
    abstract fun extractAccountNumber(bban: String): String

    object Norway : BBANParser() {
        override val countryCode = "NO"
        override fun extractBankCode(bban: String) = bban.substring(0, 4) // 4 char for bank code
        override fun extractAccountNumber(bban: String) = bban.substring(4)
    }

    companion object {
        fun fromCountryCode(code: String): BBANParser = when (code) {
            Norway.countryCode -> Norway
            else -> throw IllegalArgumentException("Unsupported country code: $code")
        }
    }
}