package com.respiroc.user.application

import com.respiroc.tenant.application.TenantService
import com.respiroc.tenant.domain.model.Tenant
import com.respiroc.tenant.domain.model.TenantPermission
import com.respiroc.tenant.domain.model.TenantRole
import com.respiroc.user.application.payload.LoginPayload
import com.respiroc.user.domain.model.*
import com.respiroc.user.domain.repository.UserRepository
import com.respiroc.user.domain.repository.UserTenantRepository
import com.respiroc.user.domain.repository.UserTenantRoleRepository
import com.respiroc.util.constant.TenantRoleCode
import com.respiroc.util.context.*
import com.respiroc.util.currency.CurrencyService
import com.respiroc.util.exception.AuthenticationException
import com.respiroc.util.exception.ResourceAlreadyExistsException
import com.respiroc.util.payload.CreateCompanyPayload
import org.springframework.security.authentication.AccountStatusUserDetailsChecker
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val tenantService: TenantService,
    private val userTenantRoleRepository: UserTenantRoleRepository,
    private val userTenantRepository: UserTenantRepository,
    private val currencyService: CurrencyService
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun signupByEmailPassword(email: String, password: String): LoginPayload {
        val existUser = userRepository.findByEmail(email)
        if (existUser != null) throw ResourceAlreadyExistsException("User already exists")

        val newUser = User()
        newUser.email = email
        newUser.passwordHash = passwordEncoder.encode(password)
        return signup(newUser)
    }

    fun loginByEmailPassword(
        email: String,
        password: String
    ): LoginPayload {
        val user = userRepository.findByEmail(email) ?: throw AuthenticationException("Email not found")
        if (!passwordEncoder.matches(password, user.passwordHash)) throw AuthenticationException("Login incorrect")

        return login(user)
    }

    fun selectTenant(user: UserContext, tenatId: Long) {
        val userDb = userRepository.findById(user.id).get()
        userDb.lastTenantId = tenatId
        userRepository.save(userDb)
    }

    fun findByIdAndTenantId(id: Long, tenantId: Long?): UserContext? {
        return userRepository.findById(id).getOrNull()?.toUserContext(tenantId)
    }

    fun findTenantRoles(userId: Long, tenantId: Long): List<TenantRoleContext> {
        // TODO : There is bug here: Collection has more than one element. There is more than one tenant
        return userRepository.findUserWithTenantRoles(userId, tenantId)!!
            .userTenants.single { it.tenantId == tenantId }
            .roles.map {
                it.tenantRole.toTenantRoleContext()
            }
    }

    fun createTenantForUser(payload: CreateCompanyPayload, user: UserContext): Tenant {
        // TODO: check for exist user tenant company
        val tenant = tenantService.createNewTenant(payload)
        val tenantRole = tenantService.findTenantRoleByCode(TenantRoleCode.OWNER)
        addUserTenantRole(tenant, tenantRole, user)
        return tenant
    }

    fun addUserTenantRole(
        tenant: Tenant,
        role: TenantRole,
        user: UserContext
    ) {
        val userTenant = getOrCreateUserTenant(user.id, tenant.id)
        val userTenantRoleId = UserTenantRoleId(tenant.id, user.id)
        val userTenantRole = UserTenantRole(userTenantRoleId, userTenant, role)
        userTenantRoleRepository.save(userTenantRole)
    }

    fun getOrCreateUserTenant(userId: Long, tenantId: Long): UserTenant {
        return userTenantRepository.findUserTenantByUserIdAndTenantId(userId, tenantId)
            ?: run {
                val userTenant = UserTenant()
                userTenant.tenantId = tenantId
                userTenant.userId = userId
                userTenantRepository.save(userTenant)
            }
    }

    // ---------------------------------
    // Private Helper
    // ---------------------------------

    private fun signup(user: User): LoginPayload {
        val savedUser: User = userRepository.saveAndFlush(user)
        return login(savedUser)
    }

    private fun login(user: User): LoginPayload {
        val springUser = SpringUser(user.toUserContext(user.lastTenantId))
        AccountStatusUserDetailsChecker().check(springUser)

        user.lastLoginAt = Instant.now()
        userRepository.save(user)

        return LoginPayload(id = user.id, tenantId = user.lastTenantId)
    }

    private fun User.toUserContext(tenantId: Long?): UserContext {
        return UserContext(
            id = this.id,
            email = this.email,
            password = this.passwordHash,
            isEnabled = this.isEnabled,
            isLocked = this.isLocked,
            currentTenant = this.toCurrentTenant(tenantId),
            tenants = this.getTenantsInfo(),
            roles = this.roles.map { it -> it.toRoleContext() }.toList()
        )
    }

    private fun User.toCurrentTenant(tenantId: Long?): UserTenantContext? {
        return tenantId
            ?.let { id -> userTenants.singleOrNull { it.tenantId == id }?.tenant }
            ?.let { tenant ->
                UserTenantContext(
                    id = tenantId,
                    companyName = tenant.getCompanyName(),
                    countryCode = currencyService.getCompanyCurrency(tenant.getCompanyCountryCode()),
                    roles = findTenantRoles(this.id, tenantId),
                    tenantSlug = tenant.slug
                )
            }
    }

    private fun User.getTenantsInfo(): List<TenantInfo> {
        return this.userTenants.map {
            val tenant = it.tenant
            TenantInfo(
                tenant.id,
                tenant.getCompanyName(),
                currencyService.getCompanyCurrency(tenant.getCompanyCountryCode())
            )
        }.sortedBy { it.id }
    }

    private fun TenantRole.toTenantRoleContext(): TenantRoleContext {
        return TenantRoleContext(
            name = this.name,
            code = this.code,
            description = this.description,
            permissions = this.tenantPermissions.map { it.toTenantPermissionContext() }.toList()
        )
    }

    private fun TenantPermission.toTenantPermissionContext(): TenantPermissionContext {
        return TenantPermissionContext(
            name = this.name,
            code = this.code,
            description = this.description
        )
    }

    private fun Role.toRoleContext(): RoleContext {
        return RoleContext(
            name = this.name,
            code = this.code,
            description = this.description,
            permissions = this.permissions.map { it.toPermissionContext() }.toList()
        )
    }

    private fun Permission.toPermissionContext(): PermissionContext {
        return PermissionContext(
            name = this.name,
            code = this.code,
            description = this.description
        )
    }
}