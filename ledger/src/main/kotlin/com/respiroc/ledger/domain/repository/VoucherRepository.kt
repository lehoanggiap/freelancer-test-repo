package com.respiroc.ledger.domain.repository

import com.respiroc.ledger.domain.model.Voucher
import com.respiroc.util.repository.CustomJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface VoucherRepository : CustomJpaRepository<Voucher, Long> {

    @Query("SELECT v FROM Voucher v WHERE v.tenantId = :tenantId AND v.date BETWEEN :startDate AND :endDate ORDER BY v.number DESC")
    fun findVoucherSummariesByTenantIdAndDateRange(
        @Param("tenantId") tenantId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<Voucher>

    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.postings WHERE v.id = :id AND v.tenantId = :tenantId")
    fun findByIdAndTenantIdWithPostings(@Param("id") id: Long, @Param("tenantId") tenantId: Long): Voucher?

    @Query("SELECT COALESCE(MAX(v.number), 0) FROM Voucher v WHERE v.tenantId = :tenantId AND EXTRACT(YEAR FROM v.date) = :year")
    fun findMaxVoucherNumberForYear(@Param("tenantId") tenantId: Long, @Param("year") year: Int): Short

    @Query(
        """
        SELECT v FROM Voucher v WHERE v.tenantId = :tenantId AND
        NOT EXISTS (SELECT 1 FROM Posting post WHERE post.voucherId = v.id)
        ORDER BY v.createdAt DESC LIMIT 1
    """
    )
    fun findFirstEmptyVoucherByTenantId(@Param("tenantId") tenantId: Long): Voucher?

    @Query(
        """
        SELECT v FROM Voucher v 
        WHERE v.tenantId = :tenantId
        AND (:search IS NULL OR v.description IS NULL OR v.description LIKE CONCAT('%', :search, '%'))
        AND (:startDate IS NULL OR v.date >= :startDate)
        AND (:endDate IS NULL OR v.date <= :endDate)
    """
    )
    fun findVouchersWithPagination(
        @Param("tenantId") tenantId: Long,
        @Param("search") search: String?,
        @Param("startDate") startDate: LocalDate?,
        @Param("endDate") endDate: LocalDate?,
        pageRequest: Pageable
    ): Page<Voucher>
}