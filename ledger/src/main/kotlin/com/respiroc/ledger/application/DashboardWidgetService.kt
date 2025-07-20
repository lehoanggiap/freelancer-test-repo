package com.respiroc.ledger.application

import com.respiroc.ledger.domain.model.AccountType
import com.respiroc.ledger.domain.model.FinancialSummaryWidget
import com.respiroc.ledger.domain.model.MonthlyTrendData
import com.respiroc.ledger.domain.model.AccountSummary
import com.respiroc.ledger.domain.model.Period
import com.respiroc.ledger.domain.model.DateRange
import com.respiroc.ledger.domain.repository.PostingRepository
import com.respiroc.util.context.ContextAwareApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional
class DashboardWidgetService(
    private val postingRepository: PostingRepository,
    private val accountService: AccountService
) : ContextAwareApi {

    companion object {
        private const val DEFAULT_MONTHS = 6
        private const val DEFAULT_TOP_ACCOUNTS_LIMIT = 5
    }

    @Transactional(readOnly = true)
    fun getFinancialSummary(period: String = Period.CURRENT_MONTH.value): FinancialSummaryWidget {
        val tenantId = tenantId()
        val dateRange = DateRange.fromPeriod(Period.fromString(period))
        
        val accountTotals = getAccountTypeTotals(tenantId, dateRange.startDate, dateRange.endDate)
        
        return FinancialSummaryWidget(
            revenue = accountTotals[AccountType.REVENUE] ?: BigDecimal.ZERO,
            expenses = accountTotals[AccountType.EXPENSE] ?: BigDecimal.ZERO,
            netIncome = (accountTotals[AccountType.REVENUE] ?: BigDecimal.ZERO) - (accountTotals[AccountType.EXPENSE] ?: BigDecimal.ZERO),
            assets = accountTotals[AccountType.ASSET] ?: BigDecimal.ZERO,
            liabilities = accountTotals[AccountType.LIABILITY] ?: BigDecimal.ZERO,
            equity = (accountTotals[AccountType.ASSET] ?: BigDecimal.ZERO) - (accountTotals[AccountType.LIABILITY] ?: BigDecimal.ZERO),
            period = period,
            startDate = dateRange.startDate,
            endDate = dateRange.endDate
        )
    }

    @Transactional(readOnly = true)
    fun getMonthlyTrends(months: Int = DEFAULT_MONTHS): List<MonthlyTrendData> {
        val tenantId = tenantId()
        val endDate = LocalDate.now()
        
        return (months - 1 downTo 0).map { monthOffset ->
            val monthStart = endDate.minusMonths(monthOffset.toLong()).withDayOfMonth(1)
            val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
            
            val accountTotals = getAccountTypeTotals(tenantId, monthStart, monthEnd)
            
            MonthlyTrendData(
                month = monthStart.month.toString(),
                year = monthStart.year,
                revenue = accountTotals[AccountType.REVENUE] ?: BigDecimal.ZERO,
                expenses = accountTotals[AccountType.EXPENSE] ?: BigDecimal.ZERO,
                netIncome = (accountTotals[AccountType.REVENUE] ?: BigDecimal.ZERO) - (accountTotals[AccountType.EXPENSE] ?: BigDecimal.ZERO)
            )
        }
    }

    @Transactional(readOnly = true)
    fun getTopAccounts(accountType: AccountType, limit: Int = DEFAULT_TOP_ACCOUNTS_LIMIT): List<AccountSummary> {
        val tenantId = tenantId()
        val dateRange = DateRange.lastMonth()
        
        val topAccounts = postingRepository.getTopAccountsByType(tenantId, accountType.name, dateRange.startDate, dateRange.endDate, limit)
        val accounts = accountService.findAllAccounts().associateBy { it.noAccountNumber }
        
        return topAccounts.mapNotNull { row ->
            createAccountSummary(row, accounts)
        }
    }



    private fun getAccountTypeTotals(tenantId: Long, startDate: LocalDate, endDate: LocalDate): Map<AccountType, BigDecimal> {
        return AccountType.values().associateWith { accountType ->
            postingRepository.getAccountTypeTotal(tenantId, accountType.name, startDate, endDate)
        }
    }

    private fun createAccountSummary(row: Array<Any>, accounts: Map<String, com.respiroc.ledger.domain.model.Account>): AccountSummary? {
        val accountNumber = row[0] as String
        val amount = row[1] as BigDecimal
        val account = accounts[accountNumber]
        
        return account?.let {
            AccountSummary(
                accountNumber = accountNumber,
                accountName = it.accountName,
                amount = amount
            )
        }
    }
} 