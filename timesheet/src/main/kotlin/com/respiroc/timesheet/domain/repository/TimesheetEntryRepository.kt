package com.respiroc.timesheet.domain.repository

import com.respiroc.timesheet.domain.model.TimesheetEntry
import com.respiroc.timesheet.domain.model.TimesheetStatus
import com.respiroc.user.domain.model.User
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TimesheetEntryRepository : CustomJpaRepository<TimesheetEntry, Int>, JpaSpecificationExecutor<TimesheetEntry> {
    
    @Query("SELECT t FROM TimesheetEntry t WHERE t.user = :user AND t.entryDate BETWEEN :startDate AND :endDate ORDER BY t.entryDate")
    fun findByUserAndDateRange(
        @Param("user") user: User,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<TimesheetEntry>
    
    @Query("SELECT t FROM TimesheetEntry t WHERE t.entryDate BETWEEN :startDate AND :endDate ORDER BY t.entryDate")
    fun findByDateRange(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<TimesheetEntry>
    
    @Query("""
        SELECT te FROM TimesheetEntry te 
        JOIN FETCH te.user u 
        JOIN FETCH te.project p 
        LEFT JOIN FETCH te.activity a 
        WHERE te.entryDate BETWEEN :startDate AND :endDate
        AND (:projectId IS NULL OR p.id = :projectId)
        AND (:employeeId IS NULL OR u.id = :employeeId)
        AND (:searchQuery IS NULL OR :searchQuery = '' OR 
             LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
             LOWER(u.email) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
             LOWER(COALESCE(a.name, '')) LIKE LOWER(CONCAT('%', :searchQuery, '%')))
        AND (:status IS NULL OR :status = '' OR te.status = :status)
        ORDER BY te.entryDate DESC, u.email ASC
    """)
    fun findFilteredMonthlyEntries(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("projectId") projectId: Int?,
        @Param("employeeId") employeeId: Int?,
        @Param("searchQuery") searchQuery: String?,
        @Param("status") status: TimesheetStatus?
    ): List<TimesheetEntry>
    
    @Query("SELECT DISTINCT te.user FROM TimesheetEntry te ORDER BY te.user.email")
    fun findDistinctUsers(): List<User>
} 