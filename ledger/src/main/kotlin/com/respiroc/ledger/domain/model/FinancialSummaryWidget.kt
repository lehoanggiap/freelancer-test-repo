package com.respiroc.ledger.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class FinancialSummaryWidget(
    val revenue: BigDecimal,
    val expenses: BigDecimal,
    val netIncome: BigDecimal,
    val assets: BigDecimal,
    val liabilities: BigDecimal,
    val equity: BigDecimal,
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate
) 