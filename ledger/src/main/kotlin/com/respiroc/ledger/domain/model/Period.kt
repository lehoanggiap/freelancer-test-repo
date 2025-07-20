package com.respiroc.ledger.domain.model

enum class Period(val value: String) {
    CURRENT_MONTH("current_month"),
    PREVIOUS_MONTH("previous_month"),
    CURRENT_YEAR("current_year");
    
    companion object {
        fun fromString(value: String): Period {
            return values().find { it.value == value } ?: CURRENT_MONTH
        }
    }
} 