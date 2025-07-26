package com.respiroc.webapp.controller.web

import com.respiroc.bank.application.BankAccountService
import com.respiroc.bank.application.payload.NewBankAccountPayload
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.NewBankAccountRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping(value = ["/bank"])
class BankWebController(private val bankAccountService: BankAccountService) : BaseController() {

    @GetMapping("/account")
    fun getBankAccounts(model: Model): String {
        addCommonAttributesForCurrentTenant(model, "Bank Accounts")
        model.addAttribute("accounts", bankAccountService.getAll())
        return "bank/bank-account"
    }

    @DeleteMapping("/account/{id}")
    @ResponseBody
    fun deleteBankAccount(@PathVariable id: Long) {
        bankAccountService.deleteById(id)
    }

    @GetMapping("/account/new")
    fun getBankAccountForm(model: Model): String {
        addCommonAttributesForCurrentTenant(model, "New Bank Account")
        model.addAttribute("account", NewBankAccountRequest())
        return "bank/bank-account-from"
    }
}

@Controller
@RequestMapping(value = ["/htmx/bank"])
class BankHTMXWebController(private val bankAccountService: BankAccountService) : BaseController() {

    @PostMapping("/account")
    fun registerBankAccount(@ModelAttribute newBackAccount: NewBankAccountRequest): String {
        val bankAccount = NewBankAccountPayload(
            bban = newBackAccount.bban,
            countryCode = newBackAccount.countryCode
        )
        bankAccountService.save(bankAccount)
        return "redirect:htmx:/bank/account"
    }
}