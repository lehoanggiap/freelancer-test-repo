package com.respiroc.bank.domain.repository

import com.respiroc.bank.domain.model.BankAccount
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BankAccountRepository : CustomJpaRepository<BankAccount, Long> {
}