package com.respiroc.timesheet.domain.repository

import com.respiroc.timesheet.domain.model.Project
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : CustomJpaRepository<Project, Int> {
    
    @Query("SELECT p FROM Project p WHERE p.isActive = true ORDER BY p.name ASC")
    fun findActiveProjectsByTenant(): List<Project>
    
    fun findByIsActiveTrue(): List<Project>
}
