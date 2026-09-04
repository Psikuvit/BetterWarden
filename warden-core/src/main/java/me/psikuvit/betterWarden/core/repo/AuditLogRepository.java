package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findAllByOrderByAtDesc(Pageable pageable);

    /** StatsService's response-time calc: the earliest matching action against one report/ticket id. */
    List<AuditLogEntry> findByTargetAndActionInOrderByAtAsc(String target, List<String> actions);
}
