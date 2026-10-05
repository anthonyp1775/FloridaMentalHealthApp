package com.flmentalhealth.controller;

import com.flmentalhealth.dto.ReportDtos;
import com.flmentalhealth.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ADMIN reporting.
 *
 * Also restricted at the filter chain level in SecurityConfig, so a
 * forgotten @PreAuthorize here could not quietly expose them.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reports", description = "Referral volume, access gaps and capacity")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/referrals")
    @Operation(summary = "Referral volume by status over a date range")
    public ReportDtos.ReferralSummary referrals(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return reportService.referralSummary(from, to);
    }

    @GetMapping("/access-gap")
    @Operation(summary = "How many accepting providers take each insurance plan type")
    public List<ReportDtos.AccessGapRow> accessGap() {
        return reportService.accessGap();
    }

    @GetMapping("/county-capacity")
    @Operation(summary = "Providers, open slots and waitlist totals by county")
    public List<ReportDtos.CountyCapacityRow> countyCapacity() {
        return reportService.countyCapacity();
    }
}
