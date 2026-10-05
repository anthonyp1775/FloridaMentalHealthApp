package com.flmentalhealth.service;

import com.flmentalhealth.dto.ReportDtos;
import com.flmentalhealth.entity.InsurancePlan;
import com.flmentalhealth.entity.ReferralRequest.Status;
import com.flmentalhealth.repository.ProviderRepository;
import com.flmentalhealth.repository.ReferralRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * ADMIN reporting.
 *
 * The access-gap report is the one that matters. It is not a count of
 * rows - it is the distance between how many clinicians take commercial
 * insurance and how many take Medicaid, which is the access barrier
 * this whole application exists to surface. It falls out of the data
 * the referral workflow already collects rather than requiring a
 * separate survey.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Wide enough to mean "all time" without special-casing nulls. */
    private static final LocalDateTime MIN = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime MAX = LocalDateTime.of(2999, 12, 31, 23, 59);

    private final ReferralRequestRepository referralRepository;
    private final ProviderRepository providerRepository;

    public ReportService(ReferralRequestRepository referralRepository,
                         ProviderRepository providerRepository) {
        this.referralRepository = referralRepository;
        this.providerRepository = providerRepository;
    }

    /**
     * Referral volume by status over a date range. Both bounds are
     * optional; omitting them covers all time.
     *
     * `to` is widened to the END of that day, so a range of
     * 2026-09-01..2026-09-30 includes referrals submitted at 4pm on the
     * 30th. Treating it as midnight would silently drop a day.
     */
    public ReportDtos.ReferralSummary referralSummary(LocalDate from, LocalDate to) {

        LocalDateTime start = (from == null) ? MIN : from.atStartOfDay();
        LocalDateTime end   = (to == null)   ? MAX : to.atTime(LocalTime.MAX);

        return new ReportDtos.ReferralSummary(
                from == null ? null : from.toString(),
                to == null ? null : to.toString(),
                referralRepository.countBySubmittedAtBetween(start, end),
                count(Status.PENDING, start, end),
                count(Status.ACCEPTED, start, end),
                count(Status.WAITLISTED, start, end),
                count(Status.DECLINED, start, end),
                count(Status.WITHDRAWN, start, end));
    }

    /** Accepting providers per insurance plan type. */
    public List<ReportDtos.AccessGapRow> accessGap() {
        return providerRepository.accessGapByPlanType().stream()
                .map(row -> new ReportDtos.AccessGapRow(
                        ((InsurancePlan.PlanType) row[0]).name(),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    /** Providers, open slots and waitlist totals, by county. */
    public List<ReportDtos.CountyCapacityRow> countyCapacity() {
        return providerRepository.capacityByCounty().stream()
                .map(row -> new ReportDtos.CountyCapacityRow(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue()))
                .toList();
    }

    private long count(Status status, LocalDateTime start, LocalDateTime end) {
        return referralRepository
                .countByStatusAndSubmittedAtBetween(status, start, end);
    }
}
