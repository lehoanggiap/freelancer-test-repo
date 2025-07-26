package com.respiroc.timesheet.application

import com.respiroc.timesheet.application.dto.*
import com.respiroc.timesheet.domain.model.*
import com.respiroc.timesheet.domain.repository.*
import com.respiroc.user.domain.model.User
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.util.context.ContextAwareApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * Exception thrown when trying to modify approved timesheet entries
 */
class ApprovedTimesheetModificationException(message: String) : IllegalStateException(message)

/**
 * Service for managing timesheet operations including weekly timesheets, monthly reports,
 * and timesheet entry management.
 * 
 * This service follows domain-driven design principles and provides clean separation
 * between business logic and data access.
 */
@Service
@Transactional
class TimesheetService(
    private val timesheetEntryRepository: TimesheetEntryRepository,
    private val projectRepository: ProjectRepository,
    private val activityRepository: ActivityRepository
) : ContextAwareApi {

    companion object {
        private val DAY_NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM")
        private const val MAX_HOURS_PER_DAY = 24
        private const val DAYS_IN_WEEK = 6L
    }

    // ========================================
    // Project and Activity Management
    // ========================================

    @Transactional(readOnly = true)
    fun getActiveProjects(tenant: Tenant): List<ProjectDto> =
        projectRepository.findActiveProjectsByTenant(tenant).map(Project::toDto)

    @Transactional(readOnly = true)
    fun getActiveActivities(tenant: Tenant, projectId: Int? = null): List<ActivityDto> {
        val project = projectId?.let { projectRepository.findById(it).orElse(null) }
        return activityRepository.findActiveActivitiesForProject(tenant, project).map(Activity::toDto)
    }

    @Transactional(readOnly = true)
    fun getActiveEmployees(tenant: Tenant): List<EmployeeDto> =
        timesheetEntryRepository.findDistinctUsersByTenant(tenant).map { user ->
            EmployeeDto(
                id = user.id,
                name = user.email, // Using email as name for now
                email = user.email
            )
        }

    // ========================================
    // Weekly Timesheet Operations
    // ========================================

    @Transactional(readOnly = true)
    fun getWeeklyTimesheet(user: User, tenant: Tenant, weekStart: LocalDate): WeeklyTimesheetDto {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val entries = timesheetEntryRepository.findByUserAndTenantAndDateRange(user, tenant, weekStart, weekEnd)
        return buildWeeklyTimesheetDto(entries, weekStart)
    }

    @Transactional
    fun saveWeeklyTimesheet(
        user: User,
        tenant: Tenant,
        weekStart: LocalDate,
        timesheetData: List<TimesheetRowDto>
    ): WeeklyTimesheetDto {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val existingEntries = timesheetEntryRepository.findByUserAndTenantAndDateRange(user, tenant, weekStart, weekEnd)
        
        validateTimesheetForModification(existingEntries, timesheetData)
        
        val (entriesToSave, entriesToDelete) = processTimesheetChanges(
            user, tenant, weekStart, timesheetData, existingEntries
        )
        
        executeTimesheetUpdates(entriesToSave, entriesToDelete)
        
        // Reload entries to get accurate data for DTO
        val updatedEntries = timesheetEntryRepository.findByUserAndTenantAndDateRange(user, tenant, weekStart, weekEnd)
        return buildWeeklyTimesheetDto(updatedEntries, weekStart)
    }

    @Transactional
    fun submitTimesheet(user: User, tenant: Tenant, weekStart: LocalDate): Boolean {
        return try {
            val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
            val entries = timesheetEntryRepository.findByUserAndTenantAndDateRange(user, tenant, weekStart, weekEnd)
            
            when {
                entries.isEmpty() -> false
                entries.any { it.status in listOf(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED) } ->
                    throw ApprovedTimesheetModificationException("Timesheet is already submitted or approved")
                else -> {
                    entries.forEach { it.status = TimesheetStatus.SUBMITTED }
                    timesheetEntryRepository.saveAll(entries)
                    true
                }
            }
        } catch (e: ApprovedTimesheetModificationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    @Transactional
    fun deleteTimesheetEntries(user: User, tenant: Tenant, entryIds: List<Int>): Boolean {
        return try {
            val entries = timesheetEntryRepository.findAllById(entryIds)
            val validEntries = filterValidEntriesForDeletion(entries, user, tenant)
            
            if (validEntries.size == entryIds.size) {
                timesheetEntryRepository.deleteAll(validEntries)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ========================================
    // Monthly Reporting
    // ========================================

    @Transactional(readOnly = true)
    fun getMonthlyReport(
        tenant: Tenant, 
        month: LocalDate,
        projectId: Int? = null,
        employeeId: Int? = null,
        searchQuery: String? = null
    ): MonthlyReportDto {
        val (monthStart, monthEnd) = calculateMonthRange(month)
        
        val filteredEntries = timesheetEntryRepository.findFilteredMonthlyEntries(
            tenant, monthStart, monthEnd, projectId, employeeId, searchQuery
        )
        
        return buildMonthlyReportDto(month, filteredEntries)
    }

    @Transactional(readOnly = true)
    fun generateTimeReportEntries(user: User, tenant: Tenant, weekStart: LocalDate): List<TimeReportEntryDto> {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val entries = timesheetEntryRepository.findByUserAndTenantAndDateRange(user, tenant, weekStart, weekEnd)
        
        return entries
            .filter { it.hours > 0.0 }
            .map { buildTimeReportEntryDto(it) }
            .sortedBy { it.date }
    }

    @Transactional(readOnly = true)
    fun getCurrentWeekStart(): LocalDate {
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        return now.with(weekFields.dayOfWeek(), 1)
    }

    private fun buildWeeklyTimesheetDto(entries: List<TimesheetEntry>, weekStart: LocalDate): WeeklyTimesheetDto {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1)
        val groupedEntries = groupEntriesByProjectActivity(entries)
        val timesheetRows = buildTimesheetRows(groupedEntries, weekStart)
        val dayTotals = calculateDayTotals(timesheetRows)
        val grandTotal = dayTotals.values.sum()
        val weekNumber = calculateWeekNumber(weekStart)
        val approvalStatus = determineApprovalStatus(entries)

        return WeeklyTimesheetDto(
            weekStart = weekStart.toString(),
            weekEnd = weekEnd.toString(),
            weekNumber = weekNumber,
            year = weekStart.year,
            rows = timesheetRows,
            dayTotals = dayTotals,
            grandTotal = grandTotal,
            isSubmitted = approvalStatus.isSubmitted,
            approvalStatus = approvalStatus.status,
            submittedAt = approvalStatus.submittedAt,
            approvedAt = approvalStatus.approvedAt,
            approvedBy = null, // Future enhancement: track approver
            rejectionReason = null // Future enhancement: track rejection reason
        )
    }

    private fun groupEntriesByProjectActivity(entries: List<TimesheetEntry>): Map<Pair<Int?, Int?>, List<TimesheetEntry>> {
        return entries
            .groupBy { Pair(it.project?.id, it.activity?.id) }
            .toList()
            .sortedBy { (_, entryList) -> entryList.minOfOrNull { it.createdAt } }
            .toMap()
    }

    private fun buildTimesheetRows(
        groupedEntries: Map<Pair<Int?, Int?>, List<TimesheetEntry>>, 
        weekStart: LocalDate
    ): List<TimesheetRowDto> {
        val rows = mutableListOf<TimesheetRowDto>()
        var rowId = 0

        groupedEntries.forEach { (projectActivityPair, entryList) ->
            val rowData = processTimesheetRowData(entryList, weekStart)
            
            rows.add(TimesheetRowDto(
                rowId = rowId++,
                projectId = projectActivityPair.first,
                activityId = projectActivityPair.second,
                hours = rowData.hours,
                comments = rowData.comments,
                entryIds = rowData.entryIds
            ))
        }

        return if (rows.isEmpty()) {
            listOf(createEmptyTimesheetRow(rowId))
        } else {
            rows
        }
    }

    private data class TimesheetRowData(
        val hours: MutableMap<String, Double>,
        val comments: MutableMap<String, String?>,
        val entryIds: MutableMap<String, Int?>
    )

    private fun processTimesheetRowData(entryList: List<TimesheetEntry>, weekStart: LocalDate): TimesheetRowData {
        val hours = mutableMapOf<String, Double>()
        val comments = mutableMapOf<String, String?>()
        val entryIds = mutableMapOf<String, Int?>()

        DAY_NAMES.forEachIndexed { dayIndex, dayName ->
            val dayDate = weekStart.plusDays(dayIndex.toLong())
            val dayEntry = entryList.find { it.entryDate.isEqual(dayDate) }
            
            hours[dayName] = dayEntry?.hours ?: 0.0
            comments[dayName] = dayEntry?.notes
            entryIds[dayName] = dayEntry?.id?.takeIf { it != -1 }
        }

        return TimesheetRowData(hours, comments, entryIds)
    }

    private fun createEmptyTimesheetRow(rowId: Int): TimesheetRowDto {
        val emptyHours = mutableMapOf<String, Double>()
        val emptyComments = mutableMapOf<String, String?>()
        
        DAY_NAMES.forEach { dayName ->
            emptyHours[dayName] = 0.0
            emptyComments[dayName] = null
        }
        
        return TimesheetRowDto(
            rowId = rowId,
            projectId = null,
            activityId = null,
            hours = emptyHours,
            comments = emptyComments
        )
    }

    private fun calculateDayTotals(rows: List<TimesheetRowDto>): Map<String, Double> {
        val dayTotals = mutableMapOf<String, Double>()
        
        DAY_NAMES.forEach { day ->
            dayTotals[day] = rows.sumOf { it.hours[day] ?: 0.0 }
        }
        
        return dayTotals
    }

    private fun calculateWeekNumber(weekStart: LocalDate): Int {
        val weekFields = WeekFields.of(Locale.getDefault())
        return weekStart.get(weekFields.weekOfWeekBasedYear())
    }

    private data class ApprovalStatusInfo(
        val isSubmitted: Boolean,
        val status: String?,
        val submittedAt: String?,
        val approvedAt: String?
    )

    private fun determineApprovalStatus(entries: List<TimesheetEntry>): ApprovalStatusInfo {
        val submittedEntry = entries.find { it.status == TimesheetStatus.SUBMITTED }
        val approvedEntry = entries.find { it.status == TimesheetStatus.APPROVED }
        val rejectedEntry = entries.find { it.status == TimesheetStatus.REJECTED }
        
        val isSubmitted = submittedEntry != null || approvedEntry != null || rejectedEntry != null
        val status = when {
            approvedEntry != null -> "APPROVED"
            rejectedEntry != null -> "REJECTED" 
            submittedEntry != null -> "PENDING"
            else -> null
        }

        return ApprovalStatusInfo(
            isSubmitted = isSubmitted,
            status = status,
            submittedAt = submittedEntry?.createdAt?.toString(),
            approvedAt = approvedEntry?.updatedAt?.toString()
        )
    }

    private fun validateTimesheetForModification(
        existingEntries: List<TimesheetEntry>,
        timesheetData: List<TimesheetRowDto>
    ) {
        // Check if any existing entries are approved
        val hasApprovedEntries = existingEntries.any { it.status == TimesheetStatus.APPROVED }
        if (hasApprovedEntries) {
            throw ApprovedTimesheetModificationException("Cannot modify approved timesheet entries")
        }
        
        // Validate timesheet data
        validateProjectActivitySelection(timesheetData)
        validateHoursRange(timesheetData)
        validateNoDuplicateProjectActivityPairs(timesheetData)
    }

    private fun validateProjectActivitySelection(timesheetData: List<TimesheetRowDto>) {
        timesheetData.forEach { row ->
            require(row.projectId != null) { "Project must be selected for all timesheet rows" }
            require(row.activityId != null) { "Activity must be selected for all timesheet rows" }
        }
    }

    private fun validateHoursRange(timesheetData: List<TimesheetRowDto>) {
        timesheetData.forEach { row ->
            row.hours.values.forEach { hours ->
                require(hours >= 0) { "Hours must be positive" }
                require(hours <= MAX_HOURS_PER_DAY) { "Hours cannot exceed $MAX_HOURS_PER_DAY per day" }
            }
        }
    }

    private fun validateNoDuplicateProjectActivityPairs(timesheetData: List<TimesheetRowDto>) {
        val projectActivityPairs = timesheetData.map { Pair(it.projectId, it.activityId) }
        val uniquePairs = projectActivityPairs.toSet()
        
        require(projectActivityPairs.size == uniquePairs.size) {
            "Duplicate project-activity combinations are not allowed. Please use one row per project-activity combination."
        }
    }

    private fun processTimesheetChanges(
        user: User,
        tenant: Tenant,
        weekStart: LocalDate,
        timesheetData: List<TimesheetRowDto>,
        existingEntries: List<TimesheetEntry>
    ): Pair<List<TimesheetEntry>, List<TimesheetEntry>> {
        val existingEntriesMap = existingEntries.associateBy { 
            Triple(it.project?.id, it.activity?.id, it.entryDate) 
        }
        val processedEntries = mutableSetOf<Triple<Int?, Int?, LocalDate>>()
        val entriesToSave = mutableListOf<TimesheetEntry>()
        val entriesToDelete = mutableListOf<TimesheetEntry>()

        // Process each timesheet row and day
        timesheetData.forEach { row ->
            DAY_NAMES.forEachIndexed { dayIndex: Int, dayName: String ->
                val entryDate = weekStart.plusDays(dayIndex.toLong())
                val entryKey = Triple(row.projectId, row.activityId, entryDate)
                processedEntries.add(entryKey)

                val updatedEntry = processTimesheetEntry(
                    user, tenant, row, dayName, entryDate, 
                    existingEntriesMap[entryKey]
                )
                
                if (updatedEntry != null) {
                    entriesToSave.add(updatedEntry)
                }
            }
        }

        // Mark unprocessed entries for deletion
        existingEntriesMap.forEach { (entryKey, entry) ->
            if (entryKey !in processedEntries) {
                entriesToDelete.add(entry)
            }
        }

        return Pair(entriesToSave, entriesToDelete)
    }

    private fun processTimesheetEntry(
        user: User,
        tenant: Tenant,
        row: TimesheetRowDto,
        dayName: String,
        entryDate: LocalDate,
        existingEntry: TimesheetEntry?
    ): TimesheetEntry? {
        val hours = row.hours[dayName] ?: 0.0
        val notes = row.comments[dayName]

        return if (existingEntry != null) {
            updateExistingEntry(existingEntry, hours, notes)
        } else {
            createNewEntry(user, tenant, row, entryDate, hours, notes)
        }
    }

    private fun updateExistingEntry(
        existingEntry: TimesheetEntry, 
        hours: Double, 
        notes: String?
    ): TimesheetEntry? {
        var hasChanges = false
        
        if (existingEntry.hours != hours) {
            existingEntry.hours = hours
            hasChanges = true
        }
        if (existingEntry.notes != notes) {
            existingEntry.notes = notes
            hasChanges = true
        }
        if (existingEntry.status != TimesheetStatus.DRAFT) {
            existingEntry.status = TimesheetStatus.DRAFT
            hasChanges = true
        }
        
        return if (hasChanges) existingEntry else null
    }

    private fun createNewEntry(
        user: User,
        tenant: Tenant,
        row: TimesheetRowDto,
        entryDate: LocalDate,
        hours: Double,
        notes: String?
    ): TimesheetEntry {
        val project = row.projectId?.let { id -> projectRepository.findById(id).orElse(null) }
        val activity = row.activityId?.let { id -> activityRepository.findById(id).orElse(null) }
        
        return TimesheetEntry().apply {
            this.user = user
            this.tenant = tenant
            this.project = project
            this.activity = activity
            this.entryDate = entryDate
            this.hours = hours
            this.notes = notes
            this.status = TimesheetStatus.DRAFT
        }
    }

    private fun executeTimesheetUpdates(
        entriesToSave: List<TimesheetEntry>,
        entriesToDelete: List<TimesheetEntry>
    ) {
        // Delete removed entries
        if (entriesToDelete.isNotEmpty()) {
            timesheetEntryRepository.deleteAll(entriesToDelete)
        }
        
        // Save new/updated entries
        if (entriesToSave.isNotEmpty()) {
            timesheetEntryRepository.saveAll(entriesToSave)
        }
    }

    private fun filterValidEntriesForDeletion(
        entries: List<TimesheetEntry>, 
        user: User, 
        tenant: Tenant
    ): List<TimesheetEntry> {
        return entries.filter { entry ->
            entry.user.id == user.id && 
            entry.tenant.id == tenant.id && 
            entry.status != TimesheetStatus.APPROVED
        }
    }

    // ========================================
    // Monthly Reporting
    // ========================================

    // Methods moved to private helper section below

    // ========================================
    // Utility Methods
    // ========================================

    // Method moved to private helper section below

    // ========================================
    // Private Helper Methods - Monthly Reporting
    // ========================================

    private fun calculateMonthRange(month: LocalDate): Pair<LocalDate, LocalDate> {
        val monthStart = month.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        return Pair(monthStart, monthEnd)
    }

    private fun buildMonthlyReportDto(month: LocalDate, entries: List<TimesheetEntry>): MonthlyReportDto {
        val reportEntries = entries.map { buildMonthlyReportEntryDto(it) }
        val statistics = calculateMonthlyStatistics(entries)
        
        return MonthlyReportDto(
            month = month.format(MONTH_FORMATTER),
            year = month.year,
            entries = reportEntries,
            totalHours = statistics.totalHours,
            totalEntries = statistics.totalEntries,
            approvedEntries = statistics.approvedEntries,
            pendingApprovals = statistics.pendingApprovals
        )
    }

    private fun buildMonthlyReportEntryDto(entry: TimesheetEntry): MonthlyReportEntryDto {
        return MonthlyReportEntryDto(
            date = entry.entryDate.toString(),
            employeeName = entry.user.email, // Future enhancement: use actual name field
            hoursWorked = entry.hours,
            projectName = entry.project?.name ?: "No Project",
            task = buildTaskDescription(entry.activity?.name, entry.description) ?: "No Task",
            notes = entry.notes,
            totalHoursLogged = entry.hours,
            approvedEntries = if (entry.status.isApproved()) 1 else 0,
            pendingApprovals = if (!entry.status.isApproved()) 1 else 0
        )
    }

    private data class MonthlyStatistics(
        val totalHours: Double,
        val totalEntries: Int,
        val approvedEntries: Int,
        val pendingApprovals: Int
    )

    private fun calculateMonthlyStatistics(entries: List<TimesheetEntry>): MonthlyStatistics {
        val totalHours = entries.sumOf { it.hours }
        val approvedCount = entries.count { it.status.isApproved() }
        val totalEntries = entries.size
        val pendingApprovals = totalEntries - approvedCount

        return MonthlyStatistics(
            totalHours = totalHours,
            totalEntries = totalEntries,
            approvedEntries = approvedCount,
            pendingApprovals = pendingApprovals
        )
    }

    private fun buildTaskDescription(activityName: String?, description: String?): String? {
        return when {
            activityName != null && description != null -> "$activityName - $description"
            activityName != null -> activityName
            description != null -> description
            else -> null
        }
    }

    private fun buildTimeReportEntryDto(entry: TimesheetEntry): TimeReportEntryDto {
        return TimeReportEntryDto(
            date = entry.entryDate.toString(),
            hours = entry.hours,
            projectName = entry.project?.name ?: "Unknown Project",
            activityName = entry.activity?.name ?: "Unknown Activity",
            task = buildTaskDescription(entry.activity?.name, entry.description) ?: "No Task",
            notes = entry.notes ?: ""
        )
    }
}

private fun Project.toDto() = ProjectDto(
    id = id,
    name = name,
    description = description,
    color = color,
    isActive = isActive
)

private fun Activity.toDto() = ActivityDto(
    id = id,
    name = name,
    description = description,
    projectId = project?.id,
    isBillable = isBillable,
    hourlyRate = hourlyRate,
    isActive = isActive
)
