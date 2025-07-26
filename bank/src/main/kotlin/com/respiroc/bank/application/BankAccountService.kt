package com.respiroc.bank.application

import com.respiroc.bank.application.payload.NewBankAccountPayload
import com.respiroc.bank.domain.model.BankAccount
import com.respiroc.bank.domain.repository.BankAccountRepository
import org.springframework.stereotype.Service

@Service
class BankAccountService(private val bankAccountRepository: BankAccountRepository) {

    fun getAll(): List<BankAccount> = bankAccountRepository.findAll()

    fun deleteById(id: Long) = bankAccountRepository.deleteById(id)

    fun save(newAccount: NewBankAccountPayload): BankAccount {
        val bbanParser = BBANParser.fromCountryCode(newAccount.countryCode)
        val account = BankAccount()
        account.accountNumber = bbanParser.extractAccountNumber(newAccount.bban)
        account.bankCode = bbanParser.extractBankCode(newAccount.bban)
        account.countryCode = newAccount.countryCode
        return bankAccountRepository.save(account)
    }
}