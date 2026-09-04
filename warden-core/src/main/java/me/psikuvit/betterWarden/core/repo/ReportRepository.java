package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusAndCreatedAtBefore(ReportStatus status, Instant cutoff);

    long countByStatus(ReportStatus status);

    /** StatsService's response-time and per-staff rollups - fetched raw, computed in Java (see DashboardController's activity chart for why). */
    List<Report> findByCreatedAtAfter(Instant cutoff);
}
