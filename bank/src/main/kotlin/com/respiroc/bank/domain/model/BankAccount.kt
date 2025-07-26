package com.respiroc.bank.domain.model

import com.respiroc.tenant.domain.model.Tenant
import jakarta.persistence.*
import jakarta.validation.constraints.Size
import org.hibernate.annotations.TenantId

@Entity
@Table(name = "bank_accounts")
open class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    open var id: Long = -1

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    open var tenantId: Long? = null

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false, insertable = false)
    open lateinit var tenant: Tenant

    @Size(max = 2)
    @Column(name = "country_code", nullable = false, length = 2)
    open lateinit var countryCode: String

    @Size(max = 20)
    @Column(name = "bank_code", nullable = false)
    open lateinit var bankCode: String

    @Size(max = 30)
    @Column(name = "account_number", nullable = false)
    open lateinit var accountNumber: String
}