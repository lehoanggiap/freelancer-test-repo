package com.respiroc.webapp.controller.web

import com.respiroc.attachment.application.VoucherAttachmentService
import com.respiroc.attachment.domain.repository.VoucherAttachmentRepository
import com.respiroc.ledger.application.VatService
import com.respiroc.ledger.application.VoucherService
import com.respiroc.util.currency.CurrencyService
import com.respiroc.util.exception.ResourceNotFoundException
import com.respiroc.webapp.constant.ShortcutRegistry
import com.respiroc.webapp.constant.ShortcutScreen
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.CreateVoucherRequest
import com.respiroc.webapp.controller.response.Callout
import com.respiroc.webapp.service.VoucherWebService
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Controller
@RequestMapping(value = ["/voucher"])
class VoucherWebController(
    private val currencyService: CurrencyService,
    private val vatService: VatService,
    private val voucherApi: VoucherService,
    private val voucherWebService: VoucherWebService,
    private val voucherAttachmentService: VoucherAttachmentService
) : BaseController() {

    @GetMapping(value = [])
    fun voucher(): String {
        return "redirect:/voucher/overview"
    }

    @GetMapping(value = ["/overview"])
    fun overview(
        @RequestParam(name = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,
        model: Model
    ): String {
        val effectiveStartDate = startDate ?: LocalDate.now().withDayOfMonth(1)
        val effectiveEndDate = endDate ?: LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())

        val vouchers = voucherApi.findVoucherSummariesByDateRange(effectiveStartDate, effectiveEndDate)

        addCommonAttributesForCurrentTenant(model, "Voucher Overview")
        model.addAttribute("vouchers", vouchers)
        model.addAttribute("startDate", effectiveStartDate)
        model.addAttribute("endDate", effectiveEndDate)
        return "voucher/overview"
    }

    @GetMapping(value = ["/new-advanced-voucher"])
    fun newAdvancedVoucher(): String {
        val emptyVoucher = voucherApi.findOrCreateEmptyVoucher()
        return "redirect:/voucher/${emptyVoucher.id}"
    }

    @GetMapping(value = ["/{id}"])
    fun editVoucher(@PathVariable id: Long, model: Model): String {
        val voucher = voucherApi.findVoucherById(id)
            ?: throw ResourceNotFoundException("Voucher not found")

        val uiPostingLines = if (voucher.postings.isNotEmpty()) {
            voucherWebService.convertPostingsToUILines(voucher.postings.toList())
        } else {
            emptyList()
        }

        val attachments = voucherAttachmentService.findAttachmentsByVoucherId(id)

        setupModelAttributes(model)
        model.addAttribute("companyCurrencyCode", countryCode())
        model.addAttribute("voucher", voucher)
        model.addAttribute("uiPostingLines", uiPostingLines)
        model.addAttribute("voucherId", id)
        model.addAttribute("voucherDate", voucher.date.toString())
        model.addAttribute("attachments", attachments)
        model.addAttribute("shortcutAction", ShortcutRegistry.getByScreen(ShortcutScreen.VOUCHERS_ADVANCED))
        return "voucher/advanced-voucher"
    }

    // -------------------------------
    // Private Helper Methods
    // -------------------------------

    private fun setupModelAttributes(model: Model) {
        val vatCodes = vatService.findAllVatCodes()
        val supportedCurrencies = currencyService.getSupportedCurrencies()

        addCommonAttributesForCurrentTenant(model, "New Voucher")
        model.addAttribute("defaultVatCode", vatCodes.first().code)
        model.addAttribute("supportedCurrencies", supportedCurrencies)
    }
}


@Controller
@RequestMapping("/htmx/voucher")
class VoucherHTMXController(
    private val currencyService: CurrencyService,
    private val vatService: VatService,
    private val voucherWebService: VoucherWebService,
    private val voucherApi: VoucherService
) : BaseController() {

    @GetMapping("/overview")
    @HxRequest
    fun overviewHTMX(
        @RequestParam(name = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,
        model: Model
    ): String {
        val effectiveStartDate = startDate ?: LocalDate.now().withDayOfMonth(1)
        val effectiveEndDate = endDate ?: LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())

        val vouchers = voucherApi.findVoucherSummariesByDateRange(effectiveStartDate, effectiveEndDate)

        model.addAttribute("vouchers", vouchers)
        model.addAttribute("startDate", effectiveStartDate)
        model.addAttribute("endDate", effectiveEndDate)
        model.addAttribute(userAttributeName, springUser())

        return "voucher/overview :: tableContent"
    }

    @PostMapping("/update/{voucherId}")
    @HxRequest
    fun updateVoucherHTMX(
        @PathVariable voucherId: Long,
        @Valid @ModelAttribute createVoucherRequest: CreateVoucherRequest,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            val errorMessages = bindingResult.allErrors.joinToString(", ") { it.defaultMessage ?: "Validation error" }
            model.addAttribute(calloutAttributeName, Callout.Error(errorMessages))
            return "fragments/r-callout"
        }

        voucherWebService.updateVoucherWithPostings(
            voucherId,
            createVoucherRequest,
            countryCode()
        )

        return "fragments/empty"
    }

    @GetMapping("/add-posting-line")
    @HxRequest
    fun addPostingLineHTMX(
        @RequestParam(defaultValue = "0") rowCounter: Int,
        model: Model
    ): String {
        val vatCodes = vatService.findAllVatCodes()
        val supportedCurrencies = currencyService.getSupportedCurrencies()
        val initialDate = LocalDate.now()

        model.addAttribute("rowCounter", rowCounter)
        model.addAttribute("defaultVatCode", vatCodes.first().code)
        model.addAttribute("companyCurrencyCode", countryCode())
        model.addAttribute("supportedCurrencies", supportedCurrencies)
        model.addAttribute("initialDate", initialDate)

        return "fragments/posting-line-row"
    }

    @PostMapping("/update-balance")
    @HxRequest
    fun updateBalanceHTMX(
        @ModelAttribute createVoucherRequest: CreateVoucherRequest,
        model: Model
    ): String {
        try {
            val (totalDebit, totalCredit, balance, isBalanced, hasValidEntries) = calculateBalance(
                createVoucherRequest,
                countryCode()
            )

            model.addAttribute("totalDebit", "%.2f".format(totalDebit))
            model.addAttribute("totalCredit", "%.2f".format(totalCredit))
            model.addAttribute("balance", "%.2f".format(balance))
            model.addAttribute("isBalanced", isBalanced)
            model.addAttribute("hasValidEntries", hasValidEntries)

            return "fragments/balance-row-simple"
        } catch (_: Exception) {
            // Return original balance on error - use company currency or fallback
            val fallbackCurrency = try {
                countryCode()
            } catch (_: Exception) {
                "NOK"
            }
            model.addAttribute("totalDebit", "0.00 $fallbackCurrency")
            model.addAttribute("totalCredit", "0.00 $fallbackCurrency")
            model.addAttribute("balance", "0.00 $fallbackCurrency")
            return "fragments/balance-row-simple"
        }
    }

    // -------------------------------
    // Private Helper
    // -------------------------------

    private fun calculateBalance(createVoucherRequest: CreateVoucherRequest, companyCurrency: String): BalanceResult {
        var totalDebit = BigDecimal.ZERO
        var totalCredit = BigDecimal.ZERO
        var hasValidEntries = false

        // Use the posting lines from the request
        createVoucherRequest.postingLines.filterNotNull().forEach { posting ->
            // Check if this posting has valid data
            if (posting.amount != null && posting.amount > BigDecimal.ZERO &&
                (posting.debitAccount.isNotBlank() || posting.creditAccount.isNotBlank())
            ) {

                // Convert to company currency if needed and round to 2 decimal places
                val convertedAmount = if (posting.currency != companyCurrency) {
                    currencyService.convertCurrency(posting.amount, posting.currency, companyCurrency)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                } else {
                    posting.amount.setScale(2, java.math.RoundingMode.HALF_UP)
                }

                // Add to totals based on account selection
                when {
                    posting.debitAccount.isNotBlank() && posting.creditAccount.isNotBlank() -> {
                        // Both debit and credit - self-balanced entry
                        totalDebit = totalDebit.add(convertedAmount)
                        totalCredit = totalCredit.add(convertedAmount)
                        hasValidEntries = true
                    }

                    posting.debitAccount.isNotBlank() -> {
                        // Only debit
                        totalDebit = totalDebit.add(convertedAmount)
                        hasValidEntries = true
                    }

                    posting.creditAccount.isNotBlank() -> {
                        // Only credit
                        totalCredit = totalCredit.add(convertedAmount)
                        hasValidEntries = true
                    }
                }
            }
        }

        val balance = totalDebit.subtract(totalCredit)
        val isBalanced = balance.abs() < BigDecimal("0.01")

        return BalanceResult(totalDebit, totalCredit, balance, isBalanced, hasValidEntries)
    }

    data class BalanceResult(
        val totalDebit: BigDecimal,
        val totalCredit: BigDecimal,
        val balance: BigDecimal,
        val isBalanced: Boolean,
        val hasValidEntries: Boolean
    )
}

@Controller
@RequestMapping("/voucher-attachment")
class VoucherAttachmentWebController(
    private val voucherAttachmentRepository: VoucherAttachmentRepository
) : BaseController() {

    @GetMapping("/document/{id}/pdf")
    fun getDocumentData(@PathVariable id: Long): ResponseEntity<String> {
        val voucherAttachment = voucherAttachmentRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found") }

        val base64Data = Base64.getEncoder().encodeToString(voucherAttachment.attachment.fileData)
        val dataUrl = "data:application/pdf;base64,$base64Data"
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
            .body("""<embed id="pdf-embed" type="application/pdf" src="$dataUrl" style="width: 100%; height: 100%; border: none;"/>""");
    }
}

@Controller
@RequestMapping("/htmx/voucher-attachment")
class VoucherAttachmentHTMXController(
    private val voucherAttachmentService: VoucherAttachmentService
) : BaseController() {

    @PostMapping("/upload")
    @HxRequest
    fun uploadFiles(
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam("voucherId") voucherId: Long,
        model: Model
    ): String {
        files.forEach { file ->
            val fileData = file.bytes
            val filename = file.originalFilename ?: "unnamed"
            val mimeType = file.contentType ?: "application/octet-stream"

            voucherAttachmentService.saveAttachment(
                voucherId = voucherId,
                fileData = fileData,
                filename = filename,
                mimeType = mimeType
            )
        }

        val updatedAttachments = voucherAttachmentService.findAttachmentsByVoucherId(voucherId)
        model.addAttribute("attachments", updatedAttachments)
        model.addAttribute("voucherId", voucherId)

        return "voucher/advanced-voucher :: attachmentsTable"
    }

    @DeleteMapping("/{id}")
    @HxRequest
    fun deleteAttachment(
        @PathVariable id: Long,
        @RequestParam("voucherId") voucherId: Long,
        model: Model
    ): String {
        voucherAttachmentService.deleteAttachment(id)

        val updatedAttachments = voucherAttachmentService.findAttachmentsByVoucherId(voucherId)
        model.addAttribute("attachments", updatedAttachments)
        model.addAttribute("voucherId", voucherId)

        return "voucher/advanced-voucher :: attachmentsTable"
    }
}