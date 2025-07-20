package com.respiroc.ledger.domain.model

import java.math.BigDecimal

data class MonthlyTrendData(
    val month: String,
    val year: Int,
    val revenue: BigDecimal,
    val expenses: BigDecimal,
    val netIncome: BigDecimal
) 