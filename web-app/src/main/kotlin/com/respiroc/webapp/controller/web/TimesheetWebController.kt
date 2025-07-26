package com.respiroc.webapp.controller.web

import com.respiroc.timesheet.application.TimesheetService
import com.respiroc.user.domain.model.User
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.DeleteTimesheetEntriesRequest
import com.respiroc.webapp.controller.request.SaveTimesheetRequest
import com.respiroc.webapp.controller.request.TimesheetRowRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Controller
@RequestMapping("/timesheet")
class TimesheetWebController(
    private val timesheetService: TimesheetService
) : BaseController() {

    @GetMapping("")
    fun timesheet(
        model: Model
    ): String {
        val user = User().apply { id = user().id }
        val tenant = Tenant().apply { id = tenantId() }
        
        val weekStart = timesheetService.getCurrentWeekStart()
        
        // Calculate previous and next week dates
        val previousWeek = weekStart.minusWeeks(1)
        val nextWeek = weekStart.plusWeeks(1)
        
        val weeklyTimesheet = timesheetService.getWeeklyTimesheet(user, tenant, weekStart)
        val projects = timesheetService.getActiveProjects(tenant)
        val activities = timesheetService.getActiveActivities(tenant)
        
        // Generate time report data using the service
        val timeReportEntries = timesheetService.generateTimeReportEntries(user, tenant, weekStart)
        
        // Create week dates for headers
        val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
        // Create SaveTimesheetRequest object for form binding
        val saveTimesheetRequest = SaveTimesheetRequest(
            weekStart = weekStart,
            rows = weeklyTimesheet.rows.map { row ->
                TimesheetRowRequest(
                    rowId = row.rowId,
                    projectId = row.projectId,
                    activityId = row.activityId,
                    hours = row.hours,
                    comments = row.comments
                )
            }
        )
        
        addCommonAttributesForCurrentTenant(model, "Timesheet")
        model.addAttribute("weeklyTimesheet", weeklyTimesheet)
        model.addAttribute("saveTimesheetRequest", saveTimesheetRequest)
        model.addAttribute("projects", projects)
        model.addAttribute("activities", activities)
        model.addAttribute("timeReportEntries", timeReportEntries)
        model.addAttribute("currentWeek", weekStart)
        model.addAttribute("previousWeek", previousWeek)
        model.addAttribute("nextWeek", nextWeek)
        model.addAttribute("weekDates", weekDates)
        model.addAttribute("dayNames", dayNames)
        model.addAttribute("employeeName", springUser().username ?: user().email)
        
        return "timesheet/timesheet"
    }

    @GetMapping("/monthly-report")
    fun monthlyReport(
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) year: String?,
        @RequestParam(required = false) projectId: Int?,
        @RequestParam(required = false) employeeId: Int?,
        @RequestParam(required = false) search: String?,
        model: Model
    ): String {
        val tenant = Tenant().apply { id = tenantId() }
        
        // Parse parameters or use defaults
        val selectedYear = year?.toIntOrNull() ?: LocalDate.now().year
        val selectedMonth = month?.toIntOrNull() ?: LocalDate.now().monthValue
        val reportDate = LocalDate.of(selectedYear, selectedMonth, 1)
        
        // Load initial data
        val monthlyReport = timesheetService.getMonthlyReport(tenant, reportDate, projectId, employeeId, search)
        val projects = timesheetService.getActiveProjects(tenant)
        val employees = timesheetService.getActiveEmployees(tenant)
        
        addCommonAttributesForCurrentTenant(model, "Monthly Time Report")
        model.addAttribute("monthlyReport", monthlyReport)
        model.addAttribute("reportEntries", monthlyReport.entries)
        model.addAttribute("reportSummary", monthlyReport)
        model.addAttribute("projects", projects)
        model.addAttribute("employees", employees)
        model.addAttribute("selectedMonth", String.format("%02d", selectedMonth))
        model.addAttribute("selectedYear", selectedYear.toString())
        model.addAttribute("selectedProjectId", projectId)
        model.addAttribute("selectedEmployeeId", employeeId)
        model.addAttribute("searchQuery", search)
        
        return "timesheet/monthly-report"
    }
}
