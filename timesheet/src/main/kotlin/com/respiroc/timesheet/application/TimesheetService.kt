package com.respiroc.timesheet.application

import com.respiroc.user.domain.model.User
import com.respiroc.timesheet.application.payload.ActivityPayload
import com.respiroc.timesheet.application.payload.EmployeePayload
import com.respiroc.timesheet.application.payload.MonthlyReportPayload
import com.respiroc.timesheet.application.payload.MonthlyReportEntryPayload
import com.respiroc.timesheet.application.payload.ProjectPayload
import com.respiroc.timesheet.application.payload.TimeReportEntryPayload
import com.respiroc.timesheet.application.payload.TimesheetRowPayload
import com.respiroc.timesheet.application.payload.WeeklyTimesheetPayload
import com.respiroc.timesheet.domain.model.Activity
import com.respiroc.timesheet.domain.model.ApprovalStatusInfo
import com.respiroc.timesheet.domain.model.MonthlyStatistics
import com.respiroc.timesheet.domain.model.Project
import com.respiroc.timesheet.domain.model.TimesheetEntry
import com.respiroc.timesheet.domain.model.TimesheetRowData
import com.respiroc.timesheet.domain.model.TimesheetStatus
import com.respiroc.timesheet.domain.repository.ActivityRepository
import com.respiroc.timesheet.domain.repository.ProjectRepository
import com.respiroc.timesheet.domain.repository.TimesheetEntryRepository
import com.respiroc.util.context.ContextAwareApi
import com.respiroc.util.exception.ApprovedTimesheetModificationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale


@Service
@Transactional
class TimesheetService(
    private val timesheetEntryRepository: TimesheetEntryRepository,
    private val projectRepository: ProjectRepository,
    private val activityRepository: ActivityRepository
) : ContextAwareApi {

    companion object {
        private val logger = LoggerFactory.getLogger(TimesheetService::class.java)
        private val DAY_NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM")
        private const val MAX_HOURS_PER_DAY = 24
        private const val DAYS_IN_WEEK = 6L
    }

    @Transactional(readOnly = true)
    fun getActiveProjects(): List<ProjectPayload> =
        projectRepository.findActiveProjects().map(Project::toPayload)

    @Transactional(readOnly = true)
    fun getActiveActivities(projectId: Int? = null): List<ActivityPayload> {
        val project = projectId?.let { projectRepository.findById(it).orElse(null) }
        return activityRepository.findActiveActivitiesForProject(project).map(Activity::toPayload)
    }

    @Transactional(readOnly = true)
    fun getActiveEmployees(): List<EmployeePayload> =
        timesheetEntryRepository.findDistinctUsers().map { user ->
            EmployeePayload(
                id = user.id,
                name = user.email,
                email = user.email
            )
        }

    @Transactional(readOnly = true)
    fun getWeeklyTimesheet(user: User, weekStart: LocalDate): WeeklyTimesheetPayload {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val entries = timesheetEntryRepository.findByUserAndDateRange(user, weekStart, weekEnd)
        
        return if (entries.isEmpty()) {
            createEmptyWeeklyTimesheetDto(weekStart)
        } else {
            buildWeeklyTimesheetDto(entries, weekStart)
        }
    }

    @Transactional
    fun saveTimesheet(user: User, timesheetData: List<TimesheetRowPayload>, weekStart: LocalDate): WeeklyTimesheetPayload {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val existingEntries = timesheetEntryRepository.findByUserAndDateRange(user, weekStart, weekEnd)
        
        validateTimesheetForModification(existingEntries, timesheetData)
        
        val (entriesToSave, entriesToDelete) = processTimesheetChanges(
            user, weekStart, timesheetData, existingEntries
        )
        
        executeTimesheetUpdates(entriesToSave, entriesToDelete)
        
        val updatedEntries = timesheetEntryRepository.findByUserAndDateRange(user, weekStart, weekEnd)
        return buildWeeklyTimesheetDto(updatedEntries, weekStart)
    }

    @Transactional
    fun submitTimesheet(user: User, weekStart: LocalDate): Boolean {
        return try {
            val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
            val entries = timesheetEntryRepository.findByUserAndDateRange(user, weekStart, weekEnd)
            
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
            logger.error("Failed to submit timesheet for week $weekStart", e)
            false
        }
    }

    @Transactional
    fun deleteTimesheetEntries(user: User, entryIds: List<Int>): Boolean {
        return try {
            val entries = timesheetEntryRepository.findAllById(entryIds)
            val validEntries = filterValidEntriesForDeletion(entries, user)
            
            if (validEntries.size == entryIds.size) {
                timesheetEntryRepository.deleteAll(validEntries)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete timesheet entries with ids $entryIds", e)
            false
        }
    }

    @Transactional(readOnly = true)
    fun getMonthlyReport(
        month: LocalDate,
        projectId: Int? = null,
        employeeId: Int? = null,
        searchQuery: String? = null,
        status: String? = null
    ): MonthlyReportPayload {
        val (monthStart, monthEnd) = calculateMonthRange(month)
        
        val statusEnum = status?.let { 
            try {
                TimesheetStatus.valueOf(it)
            } catch (e: IllegalArgumentException) {
                logger.error(e.message)
                null
            }
        }
        
        val filteredEntries = timesheetEntryRepository.findFilteredMonthlyEntries(
            monthStart, monthEnd, projectId, employeeId, searchQuery, statusEnum
        )
        
        return buildMonthlyReportDto(month, filteredEntries)
    }

    @Transactional(readOnly = true)
    fun generateTimeReportEntries(user: User, weekStart: LocalDate): List<TimeReportEntryPayload> {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK)
        val entries = timesheetEntryRepository.findByUserAndDateRange(user, weekStart, weekEnd)
        
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

    @Transactional
    fun approveTimesheetEntries(
        approverUserId: Long,
        month: LocalDate,
        employeeId: Int? = null,
        projectId: Int? = null
    ): Int {
        val (monthStart, monthEnd) = calculateMonthRange(month)
        
        val entriesToApprove = timesheetEntryRepository.findFilteredMonthlyEntries(
            monthStart, monthEnd, projectId, employeeId, null, TimesheetStatus.SUBMITTED
        )
        
        if (entriesToApprove.isEmpty()) {
            return 0
        }
        
        entriesToApprove.forEach { entry ->
            entry.status = TimesheetStatus.APPROVED
            entry.updatedAt = java.time.Instant.now()
        }
        
        timesheetEntryRepository.saveAll(entriesToApprove)
        
        logger.info("Approved ${entriesToApprove.size} timesheet entries for month ${month.format(MONTH_FORMATTER)} by user $approverUserId")
        
        return entriesToApprove.size
    }

    private fun buildWeeklyTimesheetDto(entries: List<TimesheetEntry>, weekStart: LocalDate): WeeklyTimesheetPayload {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1)
        val groupedEntries = groupEntriesByProjectActivity(entries)
        val timesheetRows = buildTimesheetRows(groupedEntries, weekStart)
        val dayTotals = calculateDayTotals(timesheetRows)
        val grandTotal = dayTotals.values.sum()
        val weekNumber = calculateWeekNumber(weekStart)
        val approvalStatus = determineApprovalStatus(entries)

        return WeeklyTimesheetPayload(
            weekStart = weekStart,
            weekEnd = weekEnd,
            weekNumber = weekNumber,
            year = weekStart.year,
            rows = timesheetRows,
            dayTotals = dayTotals,
            grandTotal = grandTotal,
            isSubmitted = approvalStatus.isSubmitted,
            approvalStatus = approvalStatus.status,
            submittedAt = approvalStatus.submittedAt,
            approvedAt = approvalStatus.approvedAt,
            approvedBy = null,
            rejectionReason = null
        )
    }

    private fun createEmptyWeeklyTimesheetDto(weekStart: LocalDate): WeeklyTimesheetPayload {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1)
        val weekNumber = calculateWeekNumber(weekStart)
        val emptyDayTotals = DAY_NAMES.associateWith { 0.0 }

        return WeeklyTimesheetPayload(
            weekStart = weekStart,
            weekEnd = weekEnd,
            weekNumber = weekNumber,
            year = weekStart.year,
            rows = emptyList(),
            dayTotals = emptyDayTotals,
            grandTotal = 0.0,
            isSubmitted = false,
            approvalStatus = null,
            submittedAt = null,
            approvedAt = null,
            approvedBy = null,
            rejectionReason = null
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
    ): List<TimesheetRowPayload> {
        val rows = mutableListOf<TimesheetRowPayload>()
        var rowId = 0

        groupedEntries.forEach { (projectActivityPair, entryList) ->
            val rowData = processTimesheetRowData(entryList, weekStart)
            
            rows.add(TimesheetRowPayload(
                rowId = rowId++,
                projectId = projectActivityPair.first,
                activityId = projectActivityPair.second,
                hours = rowData.hours,
                comments = rowData.comments,
                entryIds = rowData.entryIds
            ))
        }

        return rows
    }

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

    private fun calculateDayTotals(rows: List<TimesheetRowPayload>): Map<String, Double> {
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

    private fun determineApprovalStatus(entries: List<TimesheetEntry>): ApprovalStatusInfo {
        val submittedEntry = entries.find { it.status == TimesheetStatus.SUBMITTED }
        val approvedEntry = entries.find { it.status == TimesheetStatus.APPROVED }
        val rejectedEntry = entries.find { it.status == TimesheetStatus.REJECTED }
        
        val isSubmitted = submittedEntry != null || approvedEntry != null || rejectedEntry != null
        val status = when {
            approvedEntry != null -> TimesheetStatus.APPROVED.toString()
            rejectedEntry != null -> TimesheetStatus.REJECTED.toString()
            submittedEntry != null -> TimesheetStatus.PENDING.toString()
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
        timesheetData: List<TimesheetRowPayload>
    ) {
        val hasApprovedEntries = existingEntries.any { it.status == TimesheetStatus.APPROVED }
        if (hasApprovedEntries) {
            throw ApprovedTimesheetModificationException("Cannot modify approved timesheet entries")
        }

        validateProjectActivitySelection(timesheetData)
        validateHoursRange(timesheetData)
        validateNoDuplicateProjectActivityPairs(timesheetData)
    }

    private fun validateProjectActivitySelection(timesheetData: List<TimesheetRowPayload>) {
        timesheetData.forEach { row ->
            require(row.projectId != null) { "Project must be selected for all timesheet rows" }
            require(row.activityId != null) { "Activity must be selected for all timesheet rows" }
        }
    }

    private fun validateHoursRange(timesheetData: List<TimesheetRowPayload>) {
        timesheetData.forEach { row ->
            row.hours.values.forEach { hours ->
                require(hours >= 0) { "Hours must be positive" }
                require(hours <= MAX_HOURS_PER_DAY) { "Hours cannot exceed $MAX_HOURS_PER_DAY per day" }
            }
        }
    }

    private fun validateNoDuplicateProjectActivityPairs(timesheetData: List<TimesheetRowPayload>) {
        val projectActivityPairs = timesheetData.map { Pair(it.projectId, it.activityId) }
        val uniquePairs = projectActivityPairs.toSet()
        
        require(projectActivityPairs.size == uniquePairs.size) {
            "Duplicate project-activity combinations are not allowed. Please use one row per project-activity combination."
        }
    }

    private fun processTimesheetChanges(
        user: User,
        weekStart: LocalDate,
        timesheetData: List<TimesheetRowPayload>,
        existingEntries: List<TimesheetEntry>
    ): Pair<List<TimesheetEntry>, List<TimesheetEntry>> {
        val existingEntriesMap = existingEntries.associateBy { 
            Triple(it.project?.id, it.activity?.id, it.entryDate) 
        }
        val processedEntries = mutableSetOf<Triple<Int?, Int?, LocalDate>>()
        val entriesToSave = mutableListOf<TimesheetEntry>()
        val entriesToDelete = mutableListOf<TimesheetEntry>()

        timesheetData.forEach { row ->
            DAY_NAMES.forEachIndexed { dayIndex: Int, dayName: String ->
                val entryDate = weekStart.plusDays(dayIndex.toLong())
                val entryKey = Triple(row.projectId, row.activityId, entryDate)
                processedEntries.add(entryKey)

                val updatedEntry = processTimesheetEntry(
                    user, row, dayName, entryDate, 
                    existingEntriesMap[entryKey]
                )
                
                if (updatedEntry != null) {
                    entriesToSave.add(updatedEntry)
                }
            }
        }

        existingEntriesMap.forEach { (entryKey, entry) ->
            if (entryKey !in processedEntries) {
                entriesToDelete.add(entry)
            }
        }

        return Pair(entriesToSave, entriesToDelete)
    }

    private fun processTimesheetEntry(
        user: User,
        row: TimesheetRowPayload,
        dayName: String,
        entryDate: LocalDate,
        existingEntry: TimesheetEntry?
    ): TimesheetEntry? {
        val hours = row.hours[dayName] ?: 0.0
        val notes = row.comments[dayName]

        return if (existingEntry != null) {
            updateExistingEntry(existingEntry, hours, notes)
        } else {
            createNewEntry(user, row, entryDate, hours, notes)
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
        row: TimesheetRowPayload,
        entryDate: LocalDate,
        hours: Double,
        notes: String?
    ): TimesheetEntry {
        val project = row.projectId?.let { id -> projectRepository.findById(id).orElse(null) }
        val activity = row.activityId?.let { id -> activityRepository.findById(id).orElse(null) }
        
        return TimesheetEntry().apply {
            this.user = user
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
        if (entriesToDelete.isNotEmpty()) {
            timesheetEntryRepository.deleteAll(entriesToDelete)
        }
        
        if (entriesToSave.isNotEmpty()) {
            timesheetEntryRepository.saveAll(entriesToSave)
        }
    }

    private fun filterValidEntriesForDeletion(
        entries: List<TimesheetEntry>, 
        user: User
    ): List<TimesheetEntry> {
        return entries.filter { entry ->
            entry.user.id == user.id && 
            entry.status != TimesheetStatus.APPROVED
        }
    }

    private fun calculateMonthRange(month: LocalDate): Pair<LocalDate, LocalDate> {
        val monthStart = month.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        return Pair(monthStart, monthEnd)
    }

    private fun buildMonthlyReportDto(month: LocalDate, entries: List<TimesheetEntry>): MonthlyReportPayload {
        val reportEntries = entries.map { buildMonthlyReportEntryDto(it) }
        val statistics = calculateMonthlyStatistics(entries)
        
        return MonthlyReportPayload(
            month = month.format(MONTH_FORMATTER),
            year = month.year,
            entries = reportEntries,
            totalHours = statistics.totalHours,
            totalEntries = statistics.totalEntries,
            approvedEntries = statistics.approvedEntries,
            pendingApprovals = statistics.pendingApprovals
        )
    }

    private fun buildMonthlyReportEntryDto(entry: TimesheetEntry): MonthlyReportEntryPayload {
        return MonthlyReportEntryPayload(
            date = entry.entryDate,
            employeeName = entry.user.email,
            hoursWorked = entry.hours,
            projectName = entry.project?.name ?: "No Project",
            task = buildTaskDescription(entry.activity?.name, entry.description) ?: "No Task",
            notes = entry.notes,
            status = entry.status.toString(),
            totalHoursLogged = entry.hours,
            approvedEntries = if (entry.status.isApproved()) 1 else 0,
            pendingApprovals = if (!entry.status.isApproved()) 1 else 0
        )
    }

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

    private fun buildTimeReportEntryDto(entry: TimesheetEntry): TimeReportEntryPayload {
        return TimeReportEntryPayload(
            date = entry.entryDate,
            hours = entry.hours,
            projectName = entry.project?.name ?: "Unknown Project",
            activityName = entry.activity?.name ?: "Unknown Activity",
            task = buildTaskDescription(entry.activity?.name, entry.description) ?: "No Task",
            notes = entry.notes ?: ""
        )
    }
}

private fun Project.toPayload() = ProjectPayload(
    id = id,
    name = name,
    description = description,
    isActive = isActive
)

private fun Activity.toPayload() = ActivityPayload(
    id = id,
    name = name,
    description = description,
    projectId = project?.id,
    isBillable = isBillable,
    hourlyRate = hourlyRate,
    isActive = isActive
)
