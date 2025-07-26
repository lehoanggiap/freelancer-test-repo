package com.respiroc.timesheet.domain.repository

import com.respiroc.timesheet.domain.model.Activity
import com.respiroc.timesheet.domain.model.Project
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ActivityRepository : CustomJpaRepository<Activity, Int> {
    
    @Query("SELECT a FROM Activity a WHERE a.tenant = :tenant AND (a.project IS NULL OR a.project = :project) AND a.isActive = true ORDER BY a.name ASC")
    fun findActiveActivitiesForProject(@Param("tenant") tenant: Tenant, @Param("project") project: Project?): List<Activity>
    
    @Query("SELECT a FROM Activity a WHERE a.tenant = :tenant AND a.isActive = true ORDER BY a.name ASC")
    fun findActiveActivitiesByTenant(@Param("tenant") tenant: Tenant): List<Activity>
}
