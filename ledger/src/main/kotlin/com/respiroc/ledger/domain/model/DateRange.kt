package com.respiroc.ledger.domain.model

import java.time.LocalDate

data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    companion object {
        fun currentMonth(): DateRange {
            val now = LocalDate.now()
            return DateRange(
                startDate = now.withDayOfMonth(1),
                endDate = now.withDayOfMonth(now.lengthOfMonth())
            )
        }

        fun previousMonth(): DateRange {
            val now = LocalDate.now()
            val start = now.minusMonths(1).withDayOfMonth(1)
            return DateRange(
                startDate = start,
                endDate = start.withDayOfMonth(start.lengthOfMonth())
            )
        }

        fun currentYear(): DateRange {
            val now = LocalDate.now()
            return DateRange(
                startDate = now.withDayOfYear(1),
                endDate = now.withDayOfYear(now.lengthOfYear())
            )
        }

        fun lastMonth(): DateRange {
            val now = LocalDate.now()
            return DateRange(
                startDate = now.minusMonths(1).withDayOfMonth(1),
                endDate = now
            )
        }

        fun fromPeriod(period: Period): DateRange {
            return when (period) {
                Period.CURRENT_MONTH -> currentMonth()
                Period.PREVIOUS_MONTH -> previousMonth()
                Period.CURRENT_YEAR -> currentYear()
            }
        }
    }
} 