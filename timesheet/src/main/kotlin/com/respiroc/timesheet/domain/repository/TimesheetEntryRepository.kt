package com.respiroc.timesheet.domain.repository

import com.respiroc.timesheet.domain.model.TimesheetEntry
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.user.domain.model.User
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TimesheetEntryRepository : CustomJpaRepository<TimesheetEntry, Int>, JpaSpecificationExecutor<TimesheetEntry> {
    
    @Query("SELECT t FROM TimesheetEntry t WHERE t.user = :user AND t.tenant = :tenant AND t.entryDate BETWEEN :startDate AND :endDate ORDER BY t.entryDate")
    fun findByUserAndTenantAndDateRange(
        @Param("user") user: User,
        @Param("tenant") tenant: Tenant,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<TimesheetEntry>
    
    @Query("SELECT t FROM TimesheetEntry t WHERE t.tenant = :tenant AND t.entryDate BETWEEN :startDate AND :endDate ORDER BY t.entryDate")
    fun findByTenantAndDateRange(
        @Param("tenant") tenant: Tenant,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<TimesheetEntry>
    
    @Query("""
        SELECT te FROM TimesheetEntry te 
        JOIN FETCH te.user u 
        JOIN FETCH te.project p 
        LEFT JOIN FETCH te.activity a 
        WHERE te.tenant = :tenant 
        AND te.entryDate BETWEEN :startDate AND :endDate
        AND (:projectId IS NULL OR p.id = :projectId)
        AND (:employeeId IS NULL OR u.id = :employeeId)
        ORDER BY te.entryDate DESC, u.email ASC
    """)
    fun findFilteredMonthlyEntries(
        @Param("tenant") tenant: Tenant,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("projectId") projectId: Int?,
        @Param("employeeId") employeeId: Int?,
        @Param("searchQuery") searchQuery: String?
    ): List<TimesheetEntry>
    
    @Query("SELECT DISTINCT te.user FROM TimesheetEntry te WHERE te.tenant = :tenant ORDER BY te.user.email")
    fun findDistinctUsersByTenant(@Param("tenant") tenant: Tenant): List<User>
} 