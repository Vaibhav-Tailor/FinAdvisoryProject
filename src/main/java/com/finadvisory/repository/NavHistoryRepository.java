package com.finadvisory.repository;

import com.finadvisory.entity.NavHistory;
import com.finadvisory.entity.MfScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for nav_history table.
 * Unique constraint on (scheme_id, nav_date) is enforced at DB level —
 * duplicate inserts for same scheme + date will throw DataIntegrityViolationException,
 * which the scheduler handles gracefully.
 */
@Repository
public interface NavHistoryRepository extends JpaRepository<NavHistory, Long> {

    /**
     * Get the latest available NAV for a scheme.
     * Finds the most recent nav_date and returns that record.
     * This is the primary query used for P&L calculation on the dashboard.
     */
    @Query("SELECT n FROM NavHistory n WHERE n.scheme.id = :schemeId " +
           "AND n.navDate = (SELECT MAX(n2.navDate) FROM NavHistory n2 WHERE n2.scheme.id = :schemeId AND n2.delFlg = false)")
    Optional<NavHistory> findLatestNavBySchemeId(@Param("schemeId") Long schemeId);

    /**
     * Get NAV for a specific scheme on a specific date.
     * Used during manual transaction entry to auto-fill purchase NAV
     * based on the transaction date entered by admin.
     */
    Optional<NavHistory> findBySchemeAndNavDate(MfScheme scheme, LocalDate navDate);

    /**
     * Check if NAV already exists for a scheme on a given date.
     * Used by scheduler to skip re-insertion (UNIQUE constraint backup check).
     */
    boolean existsBySchemeAndNavDate(MfScheme scheme, LocalDate navDate);

    /**
     * Get NAV for a scheme on a date or the closest available date before it.
     * Handles weekends and market holidays — when a customer's transaction
     * falls on a non-trading day, we look back for the last available NAV.
     */
    @Query("SELECT n FROM NavHistory n WHERE n.scheme.id = :schemeId " +
           "AND n.navDate <= :date " +
           "ORDER BY n.navDate DESC")
    List<NavHistory> findClosestNavOnOrBefore(
            @Param("schemeId") Long schemeId,
            @Param("date") LocalDate date);

    /**
     * Get the latest NAV value (just the value, not the full entity).
     * Lightweight query for portfolio value calculations.
     */
    @Query("SELECT n.navValue FROM NavHistory n WHERE n.scheme.id = :schemeId " +
           "AND n.navDate = (SELECT MAX(n2.navDate) FROM NavHistory n2 WHERE n2.scheme.id = :schemeId AND n2.delFlg = false)")
    Optional<BigDecimal> findLatestNavValueBySchemeId(@Param("schemeId") Long schemeId);

    /**
     * Get latest NAVs for multiple schemes at once.
     * Used for portfolio summary — fetches all current NAVs in one query
     * instead of N queries for N schemes.
     */
    @Query("SELECT n FROM NavHistory n WHERE n.scheme.id IN :schemeIds " +
           "AND n.navDate = (SELECT MAX(n2.navDate) FROM NavHistory n2 WHERE n2.scheme.id = n.scheme.id AND n2.delFlg = false)")
    List<NavHistory> findLatestNavForSchemes(@Param("schemeIds") List<Long> schemeIds);

    /**
     * Count how many NAV records were inserted for a given date.
     * Used by scheduler to log sync summary.
     */
    @Query("SELECT COUNT(n) FROM NavHistory n WHERE n.navDate = :date")
    long countByNavDate(@Param("date") LocalDate date);
}
