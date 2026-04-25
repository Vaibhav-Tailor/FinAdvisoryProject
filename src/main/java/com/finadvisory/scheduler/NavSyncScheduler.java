package com.finadvisory.scheduler;

import com.finadvisory.entity.MfScheme;
import com.finadvisory.entity.NavHistory;
import com.finadvisory.repository.MfSchemeRepository;
import com.finadvisory.repository.NavHistoryRepository;
import com.finadvisory.scheduler.AmfiNavParser.AmfiNavRecord;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * NavSyncScheduler
 * ─────────────────────────────────────────────────────────────────
 * Fetches AMFI NAVAll.txt daily at 11 PM on weekdays and syncs:
 *  1. mf_schemes table — upserts all ~18,000 schemes (INSERT if new, UPDATE if changed)
 *  2. nav_history table — inserts today's NAV for every active scheme
 *
 * AMFI updates NAV data by approximately 10 PM IST on every business day.
 * Running at 11 PM gives a 1-hour buffer to ensure data is available.
 *
 * On first application startup, triggerManualSync() should be called
 * to load the scheme master — this can be done via the admin API
 * endpoint POST /api/admin/nav/sync.
 *
 * Cron expression: "0 0 23 * * MON-FRI"
 *  ┬ ┬ ┬  ┬ ┬  ┬
 *  │ │ │  │ │  └── Day of week: Monday to Friday only
 *  │ │ │  │ └───── Month: every month
 *  │ │ │  └─────── Day of month: every day
 *  │ │ └────────── Hour: 23 (11 PM IST)
 *  │ └──────────── Minute: 0
 *  └────────────── Second: 0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NavSyncScheduler {

    private static final String AMFI_NAV_URL =
            "https://www.amfiindia.com/spages/NAVAll.txt";

    // Batch size for DB inserts — prevents out-of-memory on ~18,000 records
    private static final int BATCH_SIZE = 200;

    private final MfSchemeRepository    mfSchemeRepository;
    private final NavHistoryRepository  navHistoryRepository;
    private final AmfiNavParser         amfiNavParser;
    private final RestTemplate          restTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduled Job
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs every weekday at 11 PM IST.
     * Fetches AMFI NAVAll.txt and syncs schemes + NAV into the database.
     */
    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledNavSync() {
        log.info("═══════════════════════════════════════════════════");
        log.info("NAV SYNC — Scheduled run started at {}", LocalDateTime.now());
        log.info("═══════════════════════════════════════════════════");
        performSync();
    }

    /**
     * Manual trigger — called on demand from AdminController.
     * Use this on first startup to load all ~18,000 schemes into DB.
     * Endpoint: POST /api/admin/nav/sync
     */
    public SyncResult triggerManualSync() {
        log.info("NAV SYNC — Manual trigger started at {}", LocalDateTime.now());
        return performSync();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Sync Logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Main sync method — fetches AMFI data, parses it, and persists to DB.
     * Returns a SyncResult summary for logging and API response.
     */
    private SyncResult performSync() {
        SyncResult result = new SyncResult();
        result.startedAt = LocalDateTime.now();

        // Step 1 — Fetch AMFI NAVAll.txt
        String rawContent = fetchAmfiData();
        if (rawContent == null) {
            result.success      = false;
            result.errorMessage = "Failed to fetch AMFI NAV data — check network or AMFI URL";
            log.error("NAV SYNC FAILED — {}", result.errorMessage);
            return result;
        }
        log.info("NAV SYNC — Fetched {} characters from AMFI", rawContent.length());

        // Step 2 — Parse into structured records
        List<AmfiNavRecord> records = amfiNavParser.parse(rawContent);
        result.totalRecordsFetched = records.size();
        log.info("NAV SYNC — Parsed {} records from AMFI NAVAll.txt", records.size());

        if (records.isEmpty()) {
            result.success      = false;
            result.errorMessage = "No records parsed — AMFI file may be empty or format changed";
            log.error("NAV SYNC FAILED — {}", result.errorMessage);
            return result;
        }

        // Step 3 — Process in batches to avoid memory issues
        int totalBatches = (int) Math.ceil((double) records.size() / BATCH_SIZE);
        log.info("NAV SYNC — Processing {} records in {} batches of {}",
                records.size(), totalBatches, BATCH_SIZE);

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end   = Math.min(i + BATCH_SIZE, records.size());
            List<AmfiNavRecord> batch = records.subList(i, end);

            SyncResult batchResult = processBatch(batch);
            result.schemesInserted += batchResult.schemesInserted;
            result.schemesUpdated  += batchResult.schemesUpdated;
            result.navInserted     += batchResult.navInserted;
            result.navSkipped      += batchResult.navSkipped;

            log.debug("NAV SYNC — Batch {}/{} done", (i / BATCH_SIZE) + 1, totalBatches);
        }

        result.success     = true;
        result.completedAt = LocalDateTime.now();

        log.info("═══════════════════════════════════════════════════");
        log.info("NAV SYNC COMPLETE at {}", result.completedAt);
        log.info("  Schemes inserted : {}", result.schemesInserted);
        log.info("  Schemes updated  : {}", result.schemesUpdated);
        log.info("  NAV rows inserted: {}", result.navInserted);
        log.info("  NAV rows skipped : {}", result.navSkipped);
        log.info("═══════════════════════════════════════════════════");

        return result;
    }

    /**
     * Processes a single batch of AmfiNavRecords.
     * Wrapped in @Transactional — if the batch fails, it rolls back cleanly
     * without affecting already-committed batches.
     */
    @Transactional
    public SyncResult processBatch(List<AmfiNavRecord> batch) {
        SyncResult result = new SyncResult();

        for (AmfiNavRecord record : batch) {
            try {
                // Step A — Upsert mf_schemes
                boolean isNew = upsertScheme(record, result);

                // Step B — Insert nav_history (only if NAV is for today or recent date)
                insertNavHistory(record, result);

            } catch (Exception e) {
                log.warn("NAV SYNC — Error processing scheme {}: {}",
                        record.amfiCode(), e.getMessage());
                // Continue with next record — don't fail entire batch for one bad record
            }
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheme Upsert
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * INSERT or UPDATE mf_schemes for the given record.
     *
     * INSERT if: no scheme with this AMFI code exists yet
     * UPDATE if: scheme exists — update name, ISINs, category (these can change)
     *
     * @return true if inserted (new scheme), false if updated (existing scheme)
     */
    private boolean upsertScheme(AmfiNavRecord record, SyncResult result) {
        Optional<MfScheme> existing = mfSchemeRepository.findByAmfiCode(record.amfiCode());

        if (existing.isEmpty()) {
            // INSERT — new scheme
            MfScheme newScheme = MfScheme.builder()
                    .amfiCode(record.amfiCode())
                    .isinGrowth(record.isinGrowth())
                    .isinDivReinvest(record.isinDivReinvest())
                    .schemeName(record.schemeName())
                    .amcName(extractAmcName(record.schemeName()))
                    .category(record.category())
                    .schemeType(record.schemeType())
                    .isActive(true)
                    .delFlg(false)
                    .build();

            mfSchemeRepository.save(newScheme);
            result.schemesInserted++;
            log.debug("NAV SYNC — New scheme inserted: {}", record.amfiCode());
            return true;

        } else {
            // UPDATE — refresh fields that AMFI can change (name, ISINs, category)
            MfScheme scheme = existing.get();
            boolean changed = false;

            if (!record.schemeName().equals(scheme.getSchemeName())) {
                scheme.setSchemeName(record.schemeName());
                changed = true;
            }
            if (record.isinGrowth() != null &&
                !record.isinGrowth().equals(scheme.getIsinGrowth())) {
                scheme.setIsinGrowth(record.isinGrowth());
                changed = true;
            }
            if (record.isinDivReinvest() != null &&
                !record.isinDivReinvest().equals(scheme.getIsinDivReinvest())) {
                scheme.setIsinDivReinvest(record.isinDivReinvest());
                changed = true;
            }
            if (record.category() != null &&
                !record.category().equals(scheme.getCategory())) {
                scheme.setCategory(record.category());
                changed = true;
            }

            if (changed) {
                mfSchemeRepository.save(scheme);
                result.schemesUpdated++;
            }
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAV History Insert
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a new NAV record into nav_history.
     * Skips if a record already exists for this scheme + date
     * (UNIQUE constraint: scheme_id + nav_date).
     *
     * The scheduler can safely re-run on the same day — duplicates
     * are caught by the existsBySchemeAndNavDate check before hitting DB.
     */
    private void insertNavHistory(AmfiNavRecord record, SyncResult result) {
        Optional<MfScheme> schemeOpt = mfSchemeRepository.findByAmfiCode(record.amfiCode());
        if (schemeOpt.isEmpty()) {
            // Scheme was just inserted in the same batch — fetch it fresh
            log.debug("NAV SYNC — Scheme not found for NAV insert: {}", record.amfiCode());
            result.navSkipped++;
            return;
        }

        MfScheme scheme = schemeOpt.get();

        // Check for duplicate before inserting
        if (navHistoryRepository.existsBySchemeAndNavDate(scheme, record.navDate())) {
            log.debug("NAV SYNC — NAV already exists for scheme {} on {} — skipping",
                    record.amfiCode(), record.navDate());
            result.navSkipped++;
            return;
        }

        try {
            NavHistory navHistory = NavHistory.builder()
                    .scheme(scheme)
                    .navDate(record.navDate())
                    .navValue(record.navValue())
                    .delFlg(false)
                    .build();

            navHistoryRepository.save(navHistory);
            result.navInserted++;

        } catch (DataIntegrityViolationException e) {
            // Concurrent insert by another thread — safe to ignore
            log.debug("NAV SYNC — Duplicate NAV insert caught by constraint for scheme {} on {}",
                    record.amfiCode(), record.navDate());
            result.navSkipped++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AMFI Fetch
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the raw content of AMFI NAVAll.txt via HTTP GET.
     * Returns null if the request fails for any reason.
     *
     * Uses Spring's RestTemplate — simple and sufficient for this use case.
     * Timeout configured in RestTemplateConfig (connect: 30s, read: 60s).
     */
    private String fetchAmfiData() {
        try {
            log.info("NAV SYNC — Fetching: {}", AMFI_NAV_URL);
            String content = restTemplate.getForObject(AMFI_NAV_URL, String.class);
            if (content == null || content.isBlank()) {
                log.error("NAV SYNC — AMFI returned empty response");
                return null;
            }
            return content;
        } catch (Exception e) {
            log.error("NAV SYNC — Failed to fetch AMFI data: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts AMC name from scheme name using known AMC name patterns.
     *
     * AMFI scheme names follow the pattern:
     * "[AMC Name] [Scheme Type] Fund - [Plan] - [Option]"
     *
     * Examples:
     * "HDFC Mid-Cap Opportunities Fund - Direct Plan - Growth"   → "HDFC Mutual Fund"
     * "SBI Blue Chip Fund - Regular Plan - Growth"               → "SBI Mutual Fund"
     * "Axis Long Term Equity Fund - Direct Plan - Growth"        → "Axis Mutual Fund"
     *
     * This is a best-effort extraction — returns null if no known AMC found.
     * The AMFI text file does not explicitly provide the AMC name as a field.
     */
    private String extractAmcName(String schemeName) {
        if (schemeName == null) return null;

        // Known AMC prefixes in AMFI data
        String[] knownAmcs = {
            "HDFC", "SBI", "ICICI Prudential", "Aditya Birla Sun Life",
            "Kotak", "Axis", "Nippon India", "UTI", "DSP", "Franklin Templeton",
            "Mirae Asset", "Tata", "L&T", "IDFC", "Canara Robeco",
            "Motilal Oswal", "Invesco", "Sundaram", "Edelweiss",
            "Quantum", "PPFAS", "WhiteOak", "Mahindra Manulife",
            "Bandhan", "Baroda BNP Paribas", "Navi", "LIC", "Quant",
            "JM Financial", "Union", "Shriram", "ITI", "NJ"
        };

        for (String amc : knownAmcs) {
            if (schemeName.startsWith(amc)) {
                return amc + " Mutual Fund";
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SyncResult DTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Summary result of a sync run.
     * Returned by triggerManualSync() and logged by scheduledNavSync().
     */
    public static class SyncResult {
        public boolean       success            = false;
        public int           totalRecordsFetched = 0;
        public int           schemesInserted    = 0;
        public int           schemesUpdated     = 0;
        public int           navInserted        = 0;
        public int           navSkipped         = 0;
        public String        errorMessage       = null;
        public LocalDateTime startedAt          = null;
        public LocalDateTime completedAt        = null;

        @Override
        public String toString() {
            return String.format(
                "SyncResult{success=%s, fetched=%d, schemes=[+%d, ~%d], nav=[+%d, skip=%d]}",
                success, totalRecordsFetched, schemesInserted, schemesUpdated,
                navInserted, navSkipped
            );
        }
    }
}
