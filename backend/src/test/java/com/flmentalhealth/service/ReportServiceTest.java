package com.flmentalhealth.service;

import com.flmentalhealth.dto.ReportDtos;
import com.flmentalhealth.entity.InsurancePlan;
import com.flmentalhealth.entity.ReferralRequest.Status;
import com.flmentalhealth.repository.ProviderRepository;
import com.flmentalhealth.repository.ReferralRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportService - the numbers a navigator actually uses.
 *
 * The access gap is the report that matters: not a count of rows, but
 * the distance between how many clinicians take commercial insurance
 * and how many take Medicaid. It falls out of data the directory
 * already holds rather than requiring a separate survey.
 *
 * The subtle test here is referralSummary_toDateIncludesTheWholeDay.
 * An inclusive end date that is treated as midnight silently drops
 * everything submitted on the last day of the range - the kind of
 * off-by-one that makes a report quietly wrong rather than obviously
 * broken.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock private ReferralRequestRepository referralRepository;
    @Mock private ProviderRepository providerRepository;

    @InjectMocks private ReportService service;

    // =================================================================
    // Referral volume
    // =================================================================

    @Test
    @DisplayName("counts referrals by status over the given range")
    void referralSummary_countsEachStatus() {
        when(referralRepository.countBySubmittedAtBetween(any(), any()))
                .thenReturn(10L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                eq(Status.PENDING), any(), any())).thenReturn(4L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                eq(Status.ACCEPTED), any(), any())).thenReturn(3L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                eq(Status.WAITLISTED), any(), any())).thenReturn(2L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                eq(Status.DECLINED), any(), any())).thenReturn(1L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                eq(Status.WITHDRAWN), any(), any())).thenReturn(0L);

        ReportDtos.ReferralSummary summary = service.referralSummary(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(summary.from()).isEqualTo("2026-09-01");
        assertThat(summary.to()).isEqualTo("2026-09-30");
        assertThat(summary.total()).isEqualTo(10L);
        assertThat(summary.pending()).isEqualTo(4L);
        assertThat(summary.accepted()).isEqualTo(3L);
        assertThat(summary.waitlisted()).isEqualTo(2L);
        assertThat(summary.declined()).isEqualTo(1L);
        assertThat(summary.withdrawn()).isZero();
    }

    /**
     * THE OFF-BY-ONE THAT MATTERS. A range of 1 Sept to 30 Sept has to
     * include a referral submitted at 4pm on the 30th. Treating the end
     * date as midnight would drop a whole day without any error.
     */
    @Test
    @DisplayName("the end date covers the whole final day, not midnight")
    void referralSummary_toDateIncludesTheWholeDay() {
        when(referralRepository.countBySubmittedAtBetween(any(), any()))
                .thenReturn(0L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                any(), any(), any())).thenReturn(0L);

        service.referralSummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        ArgumentCaptor<LocalDateTime> start =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(referralRepository)
                .countBySubmittedAtBetween(start.capture(), end.capture());

        assertThat(start.getValue())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(end.getValue().toLocalDate())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(end.getValue().getHour()).isEqualTo(23);
        assertThat(end.getValue().getMinute()).isEqualTo(59);
    }

    /**
     * Omitting both bounds means "all time". The service widens to a
     * range so broad that no real row falls outside it, which keeps the
     * repository method free of null handling.
     */
    @Test
    @DisplayName("omitting both dates widens the range to cover everything")
    void referralSummary_nullDatesMeanAllTime() {
        when(referralRepository.countBySubmittedAtBetween(any(), any()))
                .thenReturn(42L);
        when(referralRepository.countByStatusAndSubmittedAtBetween(
                any(), any(), any())).thenReturn(7L);

        ReportDtos.ReferralSummary summary = service.referralSummary(null, null);

        assertThat(summary.from()).isNull();
        assertThat(summary.to()).isNull();
        assertThat(summary.total()).isEqualTo(42L);

        ArgumentCaptor<LocalDateTime> start =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(referralRepository)
                .countBySubmittedAtBetween(start.capture(), end.capture());

        assertThat(start.getValue()).isBefore(LocalDateTime.now());
        assertThat(end.getValue()).isAfter(LocalDateTime.now());
    }

    // =================================================================
    // Access gap
    // =================================================================

    /**
     * The repository returns Object[] rows, so the mapping has to cast
     * the enum and the count. Getting that wrong is a ClassCastException
     * at runtime rather than a compile error, which is exactly why it
     * is worth a test.
     */
    @Test
    @DisplayName("maps the plan-type enum and count out of the raw query rows")
    void accessGap_mapsRawRows() {
        when(providerRepository.accessGapByPlanType()).thenReturn(List.of(
                new Object[] { InsurancePlan.PlanType.COMMERCIAL, 18L },
                new Object[] { InsurancePlan.PlanType.MEDICAID, 6L },
                new Object[] { InsurancePlan.PlanType.SLIDING_SCALE, 4L }));

        List<ReportDtos.AccessGapRow> rows = service.accessGap();

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).planType()).isEqualTo("COMMERCIAL");
        assertThat(rows.get(0).acceptingProviders()).isEqualTo(18L);
        assertThat(rows.get(1).planType()).isEqualTo("MEDICAID");
        assertThat(rows.get(1).acceptingProviders()).isEqualTo(6L);
    }

    /**
     * MySQL returns COUNT(*) as a Long, but a different driver or a
     * projection change could hand back an Integer. The mapping goes
     * through Number for that reason, so this asserts it holds.
     */
    @Test
    @DisplayName("accepts an Integer count as well as a Long")
    void accessGap_toleratesIntegerCounts() {
        /*
         * The explicit <Object[]> witness is required, not stylistic. With
         * a SINGLE argument, List.of(new Object[]{...}) resolves to the
         * varargs overload of(E...) and spreads the array, so E infers as
         * Object and the result is List<Object> - which will not fit
         * List<Object[]>. The multi-row stubs above and below bind to the
         * fixed-arity of(E,E) and of(E,E,E), so they compile without it.
         */
        when(providerRepository.accessGapByPlanType()).thenReturn(
                List.<Object[]>of(new Object[] { InsurancePlan.PlanType.MEDICARE, 5 }));

        List<ReportDtos.AccessGapRow> rows = service.accessGap();

        assertThat(rows.get(0).acceptingProviders()).isEqualTo(5L);
    }

    @Test
    @DisplayName("no rows is an empty report, not a failure")
    void accessGap_emptyIsNotAnError() {
        when(providerRepository.accessGapByPlanType()).thenReturn(List.of());

        assertThat(service.accessGap()).isEmpty();
    }

    // =================================================================
    // County capacity
    // =================================================================

    @Test
    @DisplayName("maps county, region and the three capacity figures")
    void countyCapacity_mapsRawRows() {
        when(providerRepository.capacityByCounty()).thenReturn(List.of(
                new Object[] { "Miami-Dade", "Southern", 12L, 19L, 7L },
                new Object[] { "Leon", "Northwest", 3L, 4L, 0L }));

        List<ReportDtos.CountyCapacityRow> rows = service.countyCapacity();

        assertThat(rows).hasSize(2);

        ReportDtos.CountyCapacityRow miami = rows.get(0);
        assertThat(miami.county()).isEqualTo("Miami-Dade");
        assertThat(miami.region()).isEqualTo("Southern");
        assertThat(miami.providers()).isEqualTo(12L);
        assertThat(miami.openSlots()).isEqualTo(19L);
        assertThat(miami.waitlisted()).isEqualTo(7L);

        assertThat(rows.get(1).waitlisted()).isZero();
    }

    @Test
    @DisplayName("no rows is an empty report, not a failure")
    void countyCapacity_emptyIsNotAnError() {
        when(providerRepository.capacityByCounty()).thenReturn(List.of());

        assertThat(service.countyCapacity()).isEmpty();
    }
}
