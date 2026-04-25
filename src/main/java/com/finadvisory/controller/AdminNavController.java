package com.finadvisory.controller;

import com.finadvisory.scheduler.NavSyncScheduler;
import com.finadvisory.scheduler.NavSyncScheduler.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AdminNavController
 * ─────────────────────────────────────────────────────────────────
 * Exposes admin-only endpoints for NAV sync operations.
 *
 * These endpoints are restricted to ROLE_ADMIN via @PreAuthorize.
 * Security config must have method security enabled:
 * @EnableMethodSecurity on SecurityConfig.
 *
 * IMPORTANT: POST /api/admin/nav/sync must be called ONCE manually
 * right after the first deployment to load all ~18,000 schemes
 * into mf_schemes. After that, the nightly scheduler handles it.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/nav")
@RequiredArgsConstructor
public class AdminNavController {

    private final NavSyncScheduler navSyncScheduler;

    /**
     * POST /api/admin/nav/sync
     *
     * Manually triggers the AMFI NAV sync.
     * Use this on first startup to load the scheme master.
     * Can also be used if the nightly scheduler missed a run.
     *
     * This is a synchronous call — it waits until sync completes.
     * For ~18,000 schemes, expect ~2–5 minutes on first run.
     * Subsequent runs (daily NAV only) complete in ~30–60 seconds.
     *
     * Authorization: ADMIN only
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        log.info("Manual NAV sync triggered by admin via API");

        SyncResult result = navSyncScheduler.triggerManualSync();

        Map<String, Object> response = Map.of(
            "success",            result.success,
            "totalRecordsFetched", result.totalRecordsFetched,
            "schemesInserted",    result.schemesInserted,
            "schemesUpdated",     result.schemesUpdated,
            "navInserted",        result.navInserted,
            "navSkipped",         result.navSkipped,
            "startedAt",          result.startedAt  != null ? result.startedAt.toString()  : "",
            "completedAt",        result.completedAt != null ? result.completedAt.toString() : "",
            "errorMessage",       result.errorMessage != null ? result.errorMessage : ""
        );

        return result.success
                ? ResponseEntity.ok(response)
                : ResponseEntity.internalServerError().body(response);
    }

    /**
     * GET /api/admin/nav/status
     *
     * Returns the count of schemes and latest NAV date currently in the DB.
     * Quick health check to verify sync is working correctly.
     *
     * Authorization: ADMIN only
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        // This is expanded in Step 5 (Admin module) with full NavService
        return ResponseEntity.ok(Map.of(
            "message", "NAV sync status endpoint — full implementation in Step 5 (NavService)"
        ));
    }
}
