package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findAllByOrderByAtDesc(Pageable pageable);
}
