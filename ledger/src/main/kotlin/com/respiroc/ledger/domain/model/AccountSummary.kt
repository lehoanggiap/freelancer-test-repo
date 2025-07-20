package com.respiroc.ledger.domain.model

import java.math.BigDecimal

data class AccountSummary(
    val accountNumber: String,
    val accountName: String,
    val amount: BigDecimal
) 