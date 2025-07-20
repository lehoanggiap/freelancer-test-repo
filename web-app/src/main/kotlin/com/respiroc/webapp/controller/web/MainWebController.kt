package com.respiroc.webapp.controller.web

import com.respiroc.ledger.application.DashboardWidgetService
import com.respiroc.ledger.domain.model.Period
import com.respiroc.webapp.controller.BaseController
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class MainWebController(
    private val dashboardWidgetService: DashboardWidgetService
) : BaseController() {

    @GetMapping("/")
    fun home(): String {
        return if (isUserLoggedIn()) {
            "redirect:/dashboard"
        } else {
            "redirect:/auth/login"
        }
    }

    @GetMapping("/dashboard")
    fun dashboard(
        @RequestParam(name = "period", defaultValue = "current_month") period: String,
        model: Model
    ): String {
        val financialSummary = dashboardWidgetService.getFinancialSummary(period)
        val monthlyTrends = dashboardWidgetService.getMonthlyTrends()
        val topRevenueAccounts = dashboardWidgetService.getTopAccounts(com.respiroc.ledger.domain.model.AccountType.REVENUE)
        val topExpenseAccounts = dashboardWidgetService.getTopAccounts(com.respiroc.ledger.domain.model.AccountType.EXPENSE)
        
        addCommonAttributesForCurrentTenant(model, "Dashboard")
        model.addAttribute("financialSummary", financialSummary)
        model.addAttribute("monthlyTrends", monthlyTrends)
        model.addAttribute("topRevenueAccounts", topRevenueAccounts)
        model.addAttribute("topExpenseAccounts", topExpenseAccounts)
        model.addAttribute("selectedPeriod", period)
        return "dashboard/dashboard"
    }
} 