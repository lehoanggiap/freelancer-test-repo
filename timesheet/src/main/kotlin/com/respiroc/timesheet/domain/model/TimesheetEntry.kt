package com.respiroc.timesheet.domain.model

import com.respiroc.user.domain.model.User
import com.respiroc.tenant.domain.model.Tenant
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import com.respiroc.timesheet.domain.model.TimesheetStatus

@Entity
@Table(name = "timesheet_entries")
class TimesheetEntry : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int = -1

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    lateinit var tenant: Tenant

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "project_id", nullable = true)
    var project: Project? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "activity_id", nullable = true)
    var activity: Activity? = null

    @Column(name = "entry_date", nullable = false)
    lateinit var entryDate: LocalDate

    @Column(name = "hours", nullable = false)
    var hours: Double = 0.0

    @Column(name = "description")
    var description: String? = null

    @Column(name = "notes")
    var notes: String? = null // For additional comments/notes

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TimesheetStatus = TimesheetStatus.DRAFT

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimesheetEntry) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "TimesheetEntry(id=$id, entryDate=$entryDate, hours=$hours)"
} 