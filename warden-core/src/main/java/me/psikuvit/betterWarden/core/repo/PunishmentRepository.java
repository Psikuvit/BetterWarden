package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PunishmentRepository extends JpaRepository<Punishment, Long> {

    Page<Punishment> findByUuidOrderByIssuedAtDesc(String uuid, Pageable pageable);

    List<Punishment> findByUuidAndActiveTrue(String uuid);

    List<Punishment> findByActiveTrue();

    List<Punishment> findByUuidAndTypeAndActiveTrue(String uuid, PunishmentType type);

    long countByUuidAndTypeIn(String uuid, List<PunishmentType> types);

    long countByActiveTrueAndTypeIn(List<PunishmentType> types);

    /** Dashboard's 30-day activity chart - grouped by day in Java (DashboardController), not SQL, to stay portable across SQLite/MySQL's different date functions. */
    List<Punishment> findByIssuedAtAfter(Instant cutoff);

    @Query("SELECT COUNT(p) FROM Punishment p JOIN PunishmentTemplate t ON p.templateId = t.id " +
            "WHERE p.uuid = :uuid AND t.escalationGroup = :group")
    long countByUuidAndTemplateEscalationGroup(@Param("uuid") String uuid, @Param("group") String group);

    /**
     * Panel's /punishments browser (spec §4): type/staff/server/active/date-range filters plus a
     * free-text search on reason or the target's current name - each filter is skipped (its OR
     * branch short-circuits true) when the matching parameter is null, so one query serves every
     * combination instead of building a Specification for what's ultimately 6 optional filters.
     */
    @Query("SELECT p FROM Punishment p WHERE " +
            "(:type IS NULL OR p.type = :type) AND " +
            "(:staffUuid IS NULL OR p.staffUuid = :staffUuid) AND " +
            "(:server IS NULL OR p.server = :server) AND " +
            "(:active IS NULL OR p.active = :active) AND " +
            "(:since IS NULL OR p.issuedAt >= :since) AND " +
            "(:search IS NULL OR LOWER(p.reason) LIKE :search OR EXISTS (" +
            "  SELECT 1 FROM Player pl WHERE pl.uuid = p.uuid AND LOWER(pl.lastName) LIKE :search))")
    Page<Punishment> search(@Param("type") PunishmentType type, @Param("staffUuid") String staffUuid,
                             @Param("server") String server, @Param("active") Boolean active,
                             @Param("since") Instant since, @Param("search") String search, Pageable pageable);
}
