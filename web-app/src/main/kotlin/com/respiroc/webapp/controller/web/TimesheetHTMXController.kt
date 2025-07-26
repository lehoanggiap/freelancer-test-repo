package com.respiroc.webapp.controller.web

import com.respiroc.timesheet.application.TimesheetService
import com.respiroc.timesheet.application.ApprovedTimesheetModificationException
import com.respiroc.user.domain.model.User
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.SaveTimesheetRequest
import com.respiroc.webapp.controller.request.TimesheetRowRequest
import com.respiroc.webapp.controller.request.DeleteTimesheetEntriesRequest
import com.respiroc.webapp.controller.rest.request.SubmitTimesheetRequest
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Controller
@RequestMapping("/htmx/timesheet")
class TimesheetHTMXController(
    private val timesheetService: TimesheetService
) : BaseController() {

    @GetMapping("/week")
    @HxRequest
    fun loadWeek(
        @RequestParam weekStart: String,
        model: Model
    ): String {
        try {
            val weekStartDate = LocalDate.parse(weekStart)
            loadTimesheetData(weekStartDate.toString(), model)
            return "timesheet/fragments/timesheet-container"
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Invalid week start date: $weekStart")
            loadTimesheetData(timesheetService.getCurrentWeekStart().toString(), model)
            return "timesheet/fragments/timesheet-container"
        }
    }

    @PostMapping("/save")
    @HxRequest
    fun saveTimesheet(
        @Valid @ModelAttribute saveRequest: SaveTimesheetRequest,
        bindingResult: BindingResult,
        model: Model,
        response: HttpServletResponse
    ): String {
        val user = User().apply { id = user().id }
        val tenant = Tenant().apply { id = tenantId() }
        
        try {
            // Check for validation errors
            if (bindingResult.hasErrors()) {
                response.status = 400
                model.addAttribute(errorMessageAttributeName, "Validation errors occurred")
                loadTimesheetData(saveRequest.weekStart.toString(), model)
                return "timesheet/fragments/timesheet-main-content"
            }
            
            val updatedTimesheet = timesheetService.saveWeeklyTimesheet(
                user, 
                tenant, 
                saveRequest.weekStart, 
                saveRequest.toTimesheetRowDtos()
            )
            
            // Return updated timesheet content
            loadTimesheetData(saveRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
            
        } catch (e: ApprovedTimesheetModificationException) {
            model.addAttribute(errorMessageAttributeName, "Cannot modify approved timesheet")
            loadTimesheetData(saveRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: IllegalArgumentException) {
            model.addAttribute(errorMessageAttributeName, "Invalid timesheet data: ${e.message}")
            loadTimesheetData(saveRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: IllegalStateException) {
            model.addAttribute(errorMessageAttributeName, "Invalid timesheet state: ${e.message}")
            loadTimesheetData(saveRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Save failed: ${e.message}")
            loadTimesheetData(saveRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
        }
    }

    @PostMapping("/submit")
    @HxRequest
    fun submitTimesheet(
        @ModelAttribute submitRequest: SubmitTimesheetRequest,
        model: Model
    ): String {
        val user = User().apply { id = user().id }
        val tenant = Tenant().apply { id = tenantId() }
        
        try {
            val result = timesheetService.submitTimesheet(user, tenant, submitRequest.weekStart)
            
            if (!result) {
                model.addAttribute(errorMessageAttributeName, "Failed to submit timesheet - no timesheet entries found")
            }
            
            // Return updated submit section fragment only
            loadTimesheetData(submitRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-submit"
            
        } catch (e: ApprovedTimesheetModificationException) {
            model.addAttribute(errorMessageAttributeName, e.message ?: "Cannot modify approved timesheet")
            loadTimesheetData(submitRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-submit"
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Error submitting timesheet: ${e.message}")
            loadTimesheetData(submitRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-submit"
        }
    }

    @PostMapping("/entries/delete")
    @HxRequest
    fun deleteTimesheetEntries(
        @Valid @ModelAttribute deleteRequest: DeleteTimesheetEntriesRequest,
        bindingResult: BindingResult,
        model: Model
    ): String {
        val user = User().apply { id = user().id }
        val tenant = Tenant().apply { id = tenantId() }
        
        try {
            // Check for validation errors
            if (bindingResult.hasErrors()) {
                model.addAttribute(errorMessageAttributeName, "Validation errors occurred")
                loadTimesheetData(deleteRequest.weekStart.toString(), model)
                return "timesheet/fragments/timesheet-main-content"
            }
            
            val result = timesheetService.deleteTimesheetEntries(user, tenant, deleteRequest.entryIds)
            
            if (!result) {
                model.addAttribute(errorMessageAttributeName, "Failed to delete timesheet entries")
            }
            
            loadTimesheetData(deleteRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
            
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Error deleting timesheet entries: ${e.message}")
            loadTimesheetData(deleteRequest.weekStart.toString(), model)
            return "timesheet/fragments/timesheet-main-content"
        }
    }

    private fun loadTimesheetData(week: String, model: Model) {
        val user = User().apply { id = user().id }
        val tenant = Tenant().apply { id = tenantId() }
        
        val weekStart = try { 
            LocalDate.parse(week) 
        } catch (e: Exception) { 
            timesheetService.getCurrentWeekStart() 
        }
        
        // Calculate previous and next week dates
        val previousWeek = weekStart.minusWeeks(1)
        val nextWeek = weekStart.plusWeeks(1)
        
        val weeklyTimesheet = timesheetService.getWeeklyTimesheet(user, tenant, weekStart)
        val projects = timesheetService.getActiveProjects(tenant)
        val activities = timesheetService.getActiveActivities(tenant)
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
    }

    @GetMapping("/monthly-report")
    @HxRequest
    fun getMonthlyReport(
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) year: String?, 
        @RequestParam(required = false) projectId: Int?,
        @RequestParam(required = false) employeeId: Int?,
        @RequestParam(required = false) search: String?,
        model: Model
    ): String {
        val tenant = Tenant().apply { id = tenantId() }
        
        // Parse month/year or default to current
        val selectedYear = year?.toIntOrNull() ?: LocalDate.now().year
        val selectedMonth = month?.toIntOrNull() ?: LocalDate.now().monthValue
        val reportDate = LocalDate.of(selectedYear, selectedMonth, 1)
        
        // Get filtered monthly report
        val monthlyReport = timesheetService.getMonthlyReport(
            tenant, 
            reportDate, 
            projectId, 
            employeeId, 
            search
        )
        
        // Get filter data
        val projects = timesheetService.getActiveProjects(tenant)
        val employees = timesheetService.getActiveEmployees(tenant)
        
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
        
        return "timesheet/fragments/monthly-report-table"
    }
}