package com.finadvisory.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses AMFI's NAVAll.txt file into structured AmfiNavRecord objects.
 *
 * AMFI NAVAll.txt Format:
 * ─────────────────────────────────────────────────────────────────
 * Open Ended Schemes( Debt Schemes - Banking and PSU Fund)       ← Category header line
 *                                                                 ← Blank line
 * Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date   ← Column header
 * 119551;INF209K01YN6;INF209K01YO4;Aditya Birla Sun Life...;11.3939;01-Apr-2026                    ← Data line
 * 119552;INF209K01YP1;INF209K01YQ9;Aditya Birla Sun Life...;12.4521;01-Apr-2026                    ← Data line
 *                                                                 ← Blank line (next category starts)
 * ─────────────────────────────────────────────────────────────────
 *
 * Key parsing rules:
 * - Delimiter is semicolon (;)
 * - Lines with exactly 6 semicolon-separated tokens are data lines
 * - NAV value "-1" means not yet published — skip these
 * - ISIN fields can be blank — store as null
 * - Date format: dd-MMM-yyyy (e.g., "01-Apr-2026")
 * - Category is tracked from the current header line (no semicolons)
 */
@Slf4j
@Component
public class AmfiNavParser {

    private static final String DELIMITER             = ";";
    private static final int    EXPECTED_FIELD_COUNT  = 6;
    private static final String COLUMN_HEADER_PREFIX  = "Scheme Code";
    private static final String NAV_NOT_PUBLISHED     = "-1";

    // AMFI date format: "01-Apr-2026"
    private static final DateTimeFormatter AMFI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the full content of AMFI NAVAll.txt into a list of AmfiNavRecord.
     *
     * @param rawContent Full text content of NAVAll.txt as a String
     * @return List of parsed records (only valid, published NAV records)
     */
    public List<AmfiNavRecord> parse(String rawContent) {
        List<AmfiNavRecord> records = new ArrayList<>();

        if (rawContent == null || rawContent.isBlank()) {
            log.warn("AMFI NAV content is empty — nothing to parse");
            return records;
        }

        String[] lines        = rawContent.split("\\r?\\n");
        String   currentCategory = "Uncategorized";
        int      totalLines   = lines.length;
        int      parsedCount  = 0;
        int      skippedCount = 0;

        log.info("Starting AMFI NAV parse — total lines: {}", totalLines);

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip blank lines
            if (trimmed.isEmpty()) continue;

            // Skip the column header line
            if (trimmed.startsWith(COLUMN_HEADER_PREFIX)) continue;

            // Lines with no semicolons are category headers
            if (!trimmed.contains(DELIMITER)) {
                currentCategory = extractCategory(trimmed);
                log.debug("Processing category: {}", currentCategory);
                continue;
            }

            // Try to parse as a data line
            AmfiNavRecord record = parseLine(trimmed, currentCategory);
            if (record != null) {
                records.add(record);
                parsedCount++;
            } else {
                skippedCount++;
            }
        }

        log.info("AMFI NAV parse complete — parsed: {}, skipped: {}", parsedCount, skippedCount);
        return records;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses a single semicolon-delimited data line into an AmfiNavRecord.
     * Returns null if the line is invalid or NAV is not yet published.
     *
     * Expected format:
     * Scheme Code ; ISIN Growth ; ISIN Div Reinvest ; Scheme Name ; NAV ; Date
     *    [0]             [1]           [2]                [3]        [4]   [5]
     */
    private AmfiNavRecord parseLine(String line, String category) {
        String[] fields = line.split(DELIMITER, -1); // -1 keeps trailing empty fields

        if (fields.length != EXPECTED_FIELD_COUNT) {
            log.debug("Skipping malformed line (expected {} fields, got {}): {}",
                    EXPECTED_FIELD_COUNT, fields.length, line);
            return null;
        }

        String amfiCode        = fields[0].trim();
        String isinGrowth      = nullIfBlank(fields[1].trim());
        String isinDivReinvest = nullIfBlank(fields[2].trim());
        String schemeName      = fields[3].trim();
        String navRaw          = fields[4].trim();
        String dateRaw         = fields[5].trim();

        // Skip if NAV is not published yet
        if (NAV_NOT_PUBLISHED.equals(navRaw) || navRaw.isBlank()) {
            log.debug("Skipping scheme {} — NAV not published ({})", amfiCode, navRaw);
            return null;
        }

        // Parse NAV value
        BigDecimal navValue;
        try {
            navValue = new BigDecimal(navRaw);
        } catch (NumberFormatException e) {
            log.warn("Invalid NAV value '{}' for scheme {} — skipping", navRaw, amfiCode);
            return null;
        }

        // Parse NAV date
        LocalDate navDate;
        try {
            navDate = LocalDate.parse(dateRaw, AMFI_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date '{}' for scheme {} — skipping", dateRaw, amfiCode);
            return null;
        }

        // Derive AMC name and scheme type from the category string
        // Category format: "Open Ended Schemes( Equity Scheme - Large Cap Fund)"
        // or "Close Ended Schemes( Debt Scheme )"
        String schemeType = extractSchemeType(category);
        String amcName    = null; // Will be populated from scheme name or left null

        return new AmfiNavRecord(
                amfiCode,
                isinGrowth,
                isinDivReinvest,
                schemeName,
                navValue,
                navDate,
                category,
                schemeType,
                amcName
        );
    }

    /**
     * Extracts a clean category name from AMFI category header.
     * Input:  "Open Ended Schemes( Equity Scheme - Large Cap Fund)"
     * Output: "Equity Scheme - Large Cap Fund"
     */
    private String extractCategory(String line) {
        // Extract content inside parentheses if present
        int start = line.indexOf('(');
        int end   = line.lastIndexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return line.substring(start + 1, end).trim();
        }
        return line.trim();
    }

    /**
     * Extracts scheme type from category.
     * "Open Ended Schemes( ... )" → "Open Ended"
     * "Close Ended Schemes( ... )" → "Close Ended"
     * "Interval Fund( ... )"       → "Interval"
     */
    private String extractSchemeType(String category) {
        if (category == null) return null;
        String lower = category.toLowerCase();
        if (lower.contains("open ended"))  return "Open Ended";
        if (lower.contains("close ended")) return "Close Ended";
        if (lower.contains("interval"))    return "Interval";
        return null;
    }

    /**
     * Returns null if the string is blank, otherwise returns the string as-is.
     * Used for optional ISIN fields that AMFI leaves empty.
     */
    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner record class — parsed AMFI data
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable data record representing one parsed line from AMFI NAVAll.txt.
     * Used as an intermediate transfer object between parser and scheduler.
     */
    public record AmfiNavRecord(
            String     amfiCode,
            String     isinGrowth,
            String     isinDivReinvest,
            String     schemeName,
            BigDecimal navValue,
            LocalDate  navDate,
            String     category,
            String     schemeType,
            String     amcName
    ) {}
}
