package com.respiroc.webapp.controller.rest

import com.respiroc.timesheet.application.TimesheetService
import com.respiroc.timesheet.application.dto.ProjectDto
import com.respiroc.timesheet.application.dto.ActivityDto
import com.respiroc.timesheet.application.dto.WeeklyTimesheetDto
import com.respiroc.user.domain.model.User
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.timesheet.application.ApprovedTimesheetModificationException
import com.respiroc.webapp.controller.BaseController
import com.respiroc.webapp.controller.request.SaveTimesheetRequest
import com.respiroc.webapp.controller.rest.request.SubmitTimesheetRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/timesheet")
class TimesheetRestController(
    private val timesheetService: TimesheetService
) : BaseController() {

    @PostMapping("/save")
    fun saveWeeklyTimesheet(
        @Valid @RequestBody saveRequest: SaveTimesheetRequest,
        bindingResult: BindingResult
    ): ResponseEntity<WeeklyTimesheetDto> {
        return try {
            // Check for validation errors
            if (bindingResult.hasErrors()) {
                val errors = bindingResult.allErrors.joinToString(", ") { 
                    it.defaultMessage ?: "Validation error" 
                }
                return ResponseEntity.badRequest().build()
            }
            
            val user = User().apply { id = user().id }
            val tenant = Tenant().apply { id = tenantId() }
            
            val updatedTimesheet = timesheetService.saveWeeklyTimesheet(user, tenant, saveRequest.weekStart, saveRequest.toTimesheetRowDtos())
            
            ResponseEntity.ok(updatedTimesheet)
        } catch (e: ApprovedTimesheetModificationException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/submit")
    fun submitTimesheet(@RequestBody submitRequest: SubmitTimesheetRequest): ResponseEntity<String> {
        return try {
            val user = User().apply { id = user().id }
            val tenant = Tenant().apply { id = tenantId() }
            
            val result = timesheetService.submitTimesheet(user, tenant, submitRequest.weekStart)
            
            if (result) {
                ResponseEntity.ok("Timesheet submitted successfully")
            } else {
                ResponseEntity.badRequest().body("Failed to submit timesheet - no timesheet entries found")
            }
        } catch (e: ApprovedTimesheetModificationException) {
            ResponseEntity.badRequest().body(e.message)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("Error submitting timesheet: ${e.message}")
        }
    }

    @DeleteMapping("/entries")
    fun deleteTimesheetEntries(@RequestBody entryIds: List<Int>): ResponseEntity<String> {
        return try {
            val user = User().apply { id = user().id }
            val tenant = Tenant().apply { id = tenantId() }
            
            val result = timesheetService.deleteTimesheetEntries(user, tenant, entryIds)
            
            if (result) {
                ResponseEntity.ok("Timesheet entries deleted successfully")
            } else {
                ResponseEntity.badRequest().body("Failed to delete timesheet entries")
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("Error deleting timesheet entries: ${e.message}")
        }
    }

    @GetMapping("/projects")
    fun getActiveProjects(): ResponseEntity<List<ProjectDto>> {
        val tenant = Tenant().apply { id = tenantId() }
        val projects = timesheetService.getActiveProjects(tenant)
        return ResponseEntity.ok(projects)
    }

    @GetMapping("/activities")
    fun getActiveActivities(
        @RequestParam(required = false) projectId: Int?
    ): ResponseEntity<List<ActivityDto>> {
        val tenant = Tenant().apply { id = tenantId() }
        val activities = timesheetService.getActiveActivities(tenant, projectId)
        return ResponseEntity.ok(activities)
    }
}
