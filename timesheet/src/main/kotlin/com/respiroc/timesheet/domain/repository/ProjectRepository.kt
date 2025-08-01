package com.respiroc.timesheet.domain.repository

import com.respiroc.timesheet.domain.model.Project
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : CustomJpaRepository<Project, Int> {
    
    @Query("SELECT p FROM Project p WHERE p.isActive = true ORDER BY p.name ASC")
    fun findActiveProjects(): List<Project>
}
