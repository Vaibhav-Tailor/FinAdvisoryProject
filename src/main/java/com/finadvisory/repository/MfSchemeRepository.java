package com.finadvisory.repository;

import com.finadvisory.entity.MfScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for mf_schemes table.
 * @SQLRestriction("del_flg = 'N'") on the entity ensures all queries
 * automatically exclude logically deleted records.
 */
@Repository
public interface MfSchemeRepository extends JpaRepository<MfScheme, Long> {

    /**
     * Find scheme by AMFI code.
     * Used by NAV sync scheduler to check if scheme already exists
     * before deciding to INSERT or UPDATE.
     */
    Optional<MfScheme> findByAmfiCode(String amfiCode);

    /**
     * Find scheme by Growth ISIN.
     * Used during CAS parsing — CAS PDFs contain ISINs, not AMFI codes.
     */
    Optional<MfScheme> findByIsinGrowth(String isinGrowth);

    /**
     * Find scheme by Dividend Reinvestment ISIN.
     * CAS PDFs can reference either ISIN variant.
     */
    Optional<MfScheme> findByIsinDivReinvest(String isinDivReinvest);

    /**
     * Find scheme by either ISIN (growth or dividend reinvest).
     * Used in CAS parsing for flexible scheme lookup regardless of plan type.
     */
    @Query("SELECT s FROM MfScheme s WHERE s.isinGrowth = :isin OR s.isinDivReinvest = :isin")
    Optional<MfScheme> findByEitherIsin(@Param("isin") String isin);

    /**
     * Full-text search by scheme name or AMC name.
     * Used by admin when adding a manual transaction — search-as-you-type dropdown.
     * MySQL LIKE search — sufficient for Phase 1.
     * (FULLTEXT index on DB supports this; can upgrade to native query if needed.)
     */
    @Query("SELECT s FROM MfScheme s WHERE " +
           "(LOWER(s.schemeName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(s.amcName)    LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND s.isActive = 'Y' " +
           "ORDER BY s.amcName, s.schemeName")
    List<MfScheme> searchByNameOrAmc(@Param("query") String query);

    /**
     * Check if a scheme with this AMFI code already exists.
     * Faster than findByAmfiCode when we only need existence check.
     */
    boolean existsByAmfiCode(String amfiCode);

    /**
     * Get all active schemes.
     * Used by scheduler to know which schemes need daily NAV updates.
     */
    List<MfScheme> findByIsActive(String isActive);
}
