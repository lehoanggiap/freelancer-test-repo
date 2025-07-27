package com.respiroc.timesheet.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.TenantId
import org.hibernate.annotations.UpdateTimestamp
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "projects")
class Project : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int = -1

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: Long? = null

    @Column(name = "name", nullable = false, length = 128)
    lateinit var name: String

    @Column(name = "description")
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Project) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Project(id=$id, name='$name')"
}
