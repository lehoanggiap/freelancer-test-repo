package com.respiroc.ledger.application

import com.respiroc.ledger.application.payload.*
import com.respiroc.ledger.domain.exception.AccountNotFoundException
import com.respiroc.ledger.domain.exception.InvalidPostingsException
import com.respiroc.ledger.domain.exception.InvalidVatCodeException
import com.respiroc.ledger.domain.exception.PostingsNotBalancedException
import com.respiroc.ledger.domain.model.Posting
import com.respiroc.ledger.domain.model.Voucher
import com.respiroc.ledger.domain.repository.PostingRepository
import com.respiroc.ledger.domain.repository.VoucherRepository
import com.respiroc.util.context.ContextAwareApi
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional
class VoucherService(
    private val voucherRepository: VoucherRepository,
    private val postingRepository: PostingRepository,
    private val accountService: AccountService,
    private val vatService: VatService
) : ContextAwareApi {

    fun createVoucher(payload: CreateVoucherPayload): VoucherPayload {
        val tenantId = tenantId()

        val voucherNumber = generateNextVoucherNumber(tenantId, payload.date)

        if (payload.postings.isNotEmpty()) {
            validatePostingCommands(payload.postings)
            validateBalance(payload.postings)
        }

        val voucher = createVoucherEntity(payload, tenantId, voucherNumber)
        val savedVoucher = voucherRepository.save(voucher)

        if (payload.postings.isNotEmpty()) {
            saveNonZeroPostings(payload.postings, tenantId, savedVoucher.id)
        }

        return VoucherPayload(
            id = savedVoucher.id,
            number = savedVoucher.getDisplayNumber(),
            date = savedVoucher.date,
            description = savedVoucher.description
        )
    }

    fun findOrCreateEmptyVoucher(): VoucherPayload {
        val tenantId = tenantId()

        val existingEmptyVoucher = voucherRepository.findFirstEmptyVoucherByTenantId(tenantId)
        if (existingEmptyVoucher != null) {
            return VoucherPayload(
                id = existingEmptyVoucher.id,
                number = existingEmptyVoucher.getDisplayNumber(),
                date = existingEmptyVoucher.date,
                description = existingEmptyVoucher.description
            )
        }

        val emptyVoucherPayload = CreateVoucherPayload(
            date = LocalDate.now(),
            description = null,
            postings = emptyList()
        )
        return createVoucher(emptyVoucherPayload)
    }


    @Transactional(readOnly = true)
    fun findVoucherSummariesByDateRange(startDate: LocalDate, endDate: LocalDate): List<VoucherSummaryPayload> {
        val vouchers = voucherRepository.findVoucherSummariesByTenantIdAndDateRange(
            tenantId(), startDate, endDate
        )

        return vouchers
            .filter { it.postings.isNotEmpty() }
            .map { voucher -> toVoucherSummary(voucher) }
    }

    fun updateVoucherWithPostings(payload: UpdateVoucherPayload): VoucherPayload {
        val tenantId = tenantId()

        val voucher = voucherRepository.findByIdAndTenantIdWithPostings(payload.id, tenantId)
            ?: throw IllegalArgumentException("Voucher not found")

        if (payload.postings.isNotEmpty()) {
            validatePostingCommands(payload.postings)
            validateBalance(payload.postings)
        }

        if (voucher.postings.isNotEmpty()) {
            postingRepository.deleteAll(voucher.postings)
        }

        if (payload.postings.isNotEmpty()) {
            saveNonZeroPostings(payload.postings, tenantId, payload.id)
        }

        voucher.date = payload.date
        voucher.description = payload.description
        val savedVoucher = voucherRepository.save(voucher)

        return VoucherPayload(
            id = voucher.id,
            number = voucher.getDisplayNumber(),
            date = voucher.date,
            description = savedVoucher.description
        )
    }

    @Transactional(readOnly = true)
    fun findVoucherById(id: Long): Voucher? {
        return voucherRepository.findByIdAndTenantIdWithPostings(id, tenantId())
    }

    @Transactional(readOnly = true)
    fun findVouchersWithPagination(
        tenantId: Long,
        pageRequest: Pageable,
        search: String?,
        startDate: String?,
        endDate: String?
    ): Page<VoucherSummaryPayload> {
        val parsedStartDate = startDate?.let { LocalDate.parse(it) }
        val parsedEndDate = endDate?.let { LocalDate.parse(it) }
        
        val vouchersPage = voucherRepository.findVouchersWithPagination(
            tenantId = tenantId,
            search = search,
            startDate = parsedStartDate,
            endDate = parsedEndDate,
            pageRequest = pageRequest
        )
        
        return vouchersPage.map { voucher -> toVoucherSummary(voucher) }
    }

    // -------------------------------
    // Private Helper Methods
    // -------------------------------

    private fun generateNextVoucherNumber(tenantId: Long, date: LocalDate): Short {
        val maxNumber = voucherRepository.findMaxVoucherNumberForYear(tenantId, date.year)
        return (maxNumber + 1).toShort()
    }

    private fun validatePostingCommands(postings: List<CreatePostingPayload>) {
        // Ensure there are at least 2 non-zero postings for valid double-entry bookkeeping
        val nonZeroPostingsCount = postings.count { it.amount.compareTo(BigDecimal.ZERO) != 0 }
        if (nonZeroPostingsCount < 2) {
            throw InvalidPostingsException()
        }

        postings.forEach { postingData ->
            validateAccount(postingData.accountNumber)
            validateVatCode(postingData.vatCode)
        }
    }

    private fun validateAccount(accountNumber: String) {
        if (accountService.findAccountByNumber(accountNumber) == null) {
            throw AccountNotFoundException(accountNumber)
        }
    }

    private fun validateVatCode(vatCode: String?) {
        if (vatCode != null && !vatService.vatCodeExists(vatCode)) {
            throw InvalidVatCodeException(vatCode)
        }
    }

    private fun validateBalance(postings: List<CreatePostingPayload>) {
        val totalAmount = postings.sumOf { it.amount }
        if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw PostingsNotBalancedException(totalAmount)
        }
    }

    private fun createVoucherEntity(payload: CreateVoucherPayload, tenantId: Long, voucherNumber: Short): Voucher {
        val voucher = Voucher()
        voucher.number = voucherNumber
        voucher.date = payload.date
        voucher.description = payload.description
        voucher.tenantId = tenantId
        return voucher
    }

    private fun createPostingEntity(
        postingData: CreatePostingPayload,
        tenantId: Long,
        voucherId: Long
    ): Posting {
        val posting = Posting()
        posting.accountNumber = postingData.accountNumber
        posting.amount = postingData.amount
        posting.currency = postingData.currency
        posting.postingDate = postingData.postingDate
        posting.description = postingData.description
        posting.tenantId = tenantId
        posting.originalAmount = postingData.originalAmount
        posting.originalCurrency = postingData.originalCurrency
        posting.vatCode = postingData.vatCode
        posting.voucherId = voucherId
        posting.rowNumber = postingData.rowNumber

        return posting
    }

    private fun saveNonZeroPostings(
        postings: List<CreatePostingPayload>,
        tenantId: Long,
        voucherId: Long
    ) {
        val nonZeroPostings = postings
            .filter { it.amount.compareTo(BigDecimal.ZERO) != 0 }
            .map { postingData ->
                createPostingEntity(postingData, tenantId, voucherId)
            }

        if (nonZeroPostings.isNotEmpty()) {
            postingRepository.saveAll(nonZeroPostings)
        }
    }

    private fun toVoucherSummary(voucher: Voucher) = VoucherSummaryPayload(
        id = voucher.id,
        number = voucher.getDisplayNumber(),
        postings = voucher.postings.map { posting ->
            PostingSummaryPayload(
                id = posting.id,
                date = posting.postingDate,
                description = posting.description,
                accountNumber = posting.accountNumber,
                accountName = accountService.findAccountByNumber(posting.accountNumber)?.accountName,
                vatCode = posting.vatCode,
                amount = posting.amount
            )
        }
    )
}
