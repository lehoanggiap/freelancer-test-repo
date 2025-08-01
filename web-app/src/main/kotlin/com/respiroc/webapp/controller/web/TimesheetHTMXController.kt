package com.respiroc.webapp.controller.web

import com.respiroc.timesheet.application.TimesheetService
import com.respiroc.util.exception.ApprovedTimesheetModificationException

import com.respiroc.timesheet.domain.model.TimesheetStatus
import com.respiroc.user.domain.model.User
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.SaveTimesheetRequest
import com.respiroc.webapp.controller.request.TimesheetRowRequest
import com.respiroc.webapp.controller.request.DeleteTimesheetEntriesRequest
import com.respiroc.webapp.controller.request.ApproveTimesheetRequest
import com.respiroc.webapp.controller.rest.request.SubmitTimesheetRequest
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
            loadTimesheetData(weekStartDate, model)
            return "timesheet/fragments/timesheet-container"
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Invalid week start date: $weekStart")
            loadTimesheetData(timesheetService.getCurrentWeekStart(), model)
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
        
        try {
            if (bindingResult.hasErrors()) {
                response.status = HttpStatus.BAD_REQUEST.value()
                model.addAttribute(errorMessageAttributeName, "Validation errors occurred")
                loadTimesheetData(saveRequest.weekStart, model)
                return "timesheet/fragments/timesheet-main-content"
            }
            
            val updatedTimesheet = timesheetService.saveTimesheet(
                user, 
                saveRequest.toTimesheetRowPayloads(),
                saveRequest.weekStart
            )
            
            loadTimesheetData(saveRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
            
        } catch (e: ApprovedTimesheetModificationException) {
            response.status = HttpStatus.CONFLICT.value()
            model.addAttribute(errorMessageAttributeName, "Cannot modify approved timesheet")
            loadTimesheetData(saveRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: IllegalArgumentException) {
            response.status = HttpStatus.BAD_REQUEST.value()
            model.addAttribute(errorMessageAttributeName, "Invalid timesheet data: ${e.message}")
            loadTimesheetData(saveRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: IllegalStateException) {
            response.status = HttpStatus.BAD_REQUEST.value()
            model.addAttribute(errorMessageAttributeName, "Invalid timesheet state: ${e.message}")
            loadTimesheetData(saveRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
        } catch (e: Exception) {
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            model.addAttribute(errorMessageAttributeName, "Save failed: ${e.message}")
            loadTimesheetData(saveRequest.weekStart, model)
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
        
        try {
            val result = timesheetService.submitTimesheet(user, submitRequest.weekStart)
            
            if (!result) {
                model.addAttribute(errorMessageAttributeName, "Failed to submit timesheet - no timesheet entries found")
            }
            
            loadTimesheetData(submitRequest.weekStart, model)
            return "timesheet/fragments/timesheet-submit"
            
        } catch (e: ApprovedTimesheetModificationException) {
            model.addAttribute(errorMessageAttributeName, e.message ?: "Cannot modify approved timesheet")
            loadTimesheetData(submitRequest.weekStart, model)
            return "timesheet/fragments/timesheet-submit"
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Error submitting timesheet: ${e.message}")
            loadTimesheetData(submitRequest.weekStart, model)
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
        
        try {
            if (bindingResult.hasErrors()) {
                model.addAttribute(errorMessageAttributeName, "Validation errors occurred")
                loadTimesheetData(deleteRequest.weekStart, model)
                return "timesheet/fragments/timesheet-main-content"
            }
            
            val result = timesheetService.deleteTimesheetEntries(user, deleteRequest.entryIds)
            
            if (!result) {
                model.addAttribute(errorMessageAttributeName, "Failed to delete timesheet entries")
            }
            
            loadTimesheetData(deleteRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
            
        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Error deleting timesheet entries: ${e.message}")
            loadTimesheetData(deleteRequest.weekStart, model)
            return "timesheet/fragments/timesheet-main-content"
        }
    }

    private fun loadTimesheetData(weekStart: LocalDate, model: Model) {
        val user = User().apply { id = user().id }
        
        val previousWeek = weekStart.minusWeeks(1)
        val nextWeek = weekStart.plusWeeks(1)
        
        val weeklyTimesheet = timesheetService.getWeeklyTimesheet(user, weekStart)
        val projects = timesheetService.getActiveProjects()
        val activities = timesheetService.getActiveActivities()
        val timeReportEntries = timesheetService.generateTimeReportEntries(user, weekStart)
        
        val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
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
        @RequestParam(required = false) status: String?,
        model: Model
    ): String {
        val selectedYear = year?.toIntOrNull() ?: LocalDate.now().year
        val selectedMonth = month?.toIntOrNull() ?: LocalDate.now().monthValue
        val reportDate = LocalDate.of(selectedYear, selectedMonth, 1)
        
        return loadMonthlyReportData(reportDate, projectId, employeeId, search, status, model)
    }

    @PostMapping("/monthly-report/approve")
    @HxRequest
    fun approveTimesheetEntries(
        @Valid @ModelAttribute approveRequest: ApproveTimesheetRequest,
        bindingResult: BindingResult,
        model: Model,
        response: HttpServletResponse
    ): String {
        try {
            if (bindingResult.hasErrors()) {
                response.status = 400
                model.addAttribute(errorMessageAttributeName, "Invalid approval request")
                return loadMonthlyReportData(approveRequest.month, approveRequest.projectId, approveRequest.employeeId, null, approveRequest.status, model)
            }

            val approverUserId = user().id
            val approvedCount = timesheetService.approveTimesheetEntries(
                approverUserId = approverUserId,
                month = approveRequest.month,
                employeeId = approveRequest.employeeId,
                projectId = approveRequest.projectId
            )

            if (approvedCount == 0) {
                model.addAttribute(errorMessageAttributeName, "No submitted timesheet entries found to approve")
            } else {
                model.addAttribute("successMessage", "Successfully approved $approvedCount timesheet entries")
            }

            return loadMonthlyReportData(approveRequest.month, approveRequest.projectId, approveRequest.employeeId, null, approveRequest.status, model)

        } catch (e: Exception) {
            model.addAttribute(errorMessageAttributeName, "Error approving timesheet entries: ${e.message}")
            return loadMonthlyReportData(approveRequest.month, approveRequest.projectId, approveRequest.employeeId, null, approveRequest.status, model)
        }
    }

    @PostMapping("/add-row")
    @HxRequest
    fun addNewRow(
        @RequestParam weekStart: String,
        model: Model,
        response: HttpServletResponse
    ): String {
        try {
            val weekStartDate = LocalDate.parse(weekStart)
            val user = User().apply { id = user().id }
            
            val weeklyTimesheet = timesheetService.getWeeklyTimesheet(user, weekStartDate)
            val projects = timesheetService.getActiveProjects()
            val activities = timesheetService.getActiveActivities()
            
            if (weeklyTimesheet.approvalStatus == TimesheetStatus.APPROVED.toString()) {
                response.status = HttpStatus.FORBIDDEN.value()
                model.addAttribute(errorMessageAttributeName, "Cannot add rows to approved timesheet")
                return "timesheet/fragments/error-message"
            }
            
            val newRowIndex = weeklyTimesheet.rows.size
            val newRowId = (weeklyTimesheet.rows.maxOfOrNull { it.rowId } ?: 0) + 1
            
            model.addAttribute("projects", projects)
            model.addAttribute("activities", activities)
            model.addAttribute("newRowIndex", newRowIndex)
            model.addAttribute("newRowId", newRowId)
            model.addAttribute("weekStart", weekStartDate)
            
            return "timesheet/fragments/new-timesheet-row"
            
        } catch (e: java.time.format.DateTimeParseException) {
            response.status = HttpStatus.BAD_REQUEST.value()
            model.addAttribute(errorMessageAttributeName, "Invalid week start date format: $weekStart")
            return "timesheet/fragments/error-message"
        } catch (e: Exception) {
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            model.addAttribute(errorMessageAttributeName, "Error adding new row: ${e.message}")
            return "timesheet/fragments/error-message"
        }
    }

    private fun loadMonthlyReportData(
        month: LocalDate,
        projectId: Int?,
        employeeId: Int?,
        search: String?,
        status: String?,
        model: Model
    ): String {
        val monthlyReport = timesheetService.getMonthlyReport(month, projectId, employeeId, search, status)
        
        val projects = timesheetService.getActiveProjects()
        val employees = timesheetService.getActiveEmployees()
        
        model.addAttribute("monthlyReport", monthlyReport)
        model.addAttribute("reportEntries", monthlyReport.entries)
        model.addAttribute("reportSummary", monthlyReport)
        model.addAttribute("projects", projects)
        model.addAttribute("employees", employees)
        model.addAttribute("selectedMonth", String.format("%02d", month.monthValue))
        model.addAttribute("selectedYear", month.year.toString())
        model.addAttribute("selectedProjectId", projectId)
        model.addAttribute("selectedEmployeeId", employeeId)
        model.addAttribute("searchQuery", search)
        model.addAttribute("selectedStatus", status)
        
        return "timesheet/fragments/monthly-report-table"
    }
}