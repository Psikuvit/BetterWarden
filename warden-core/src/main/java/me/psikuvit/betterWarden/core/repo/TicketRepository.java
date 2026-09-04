package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByAssigneeAndStatusNotOrderByCreatedAtDesc(String assignee, TicketStatus excludedStatus);

    List<Ticket> findByStatusAndCreatedAtBefore(TicketStatus status, Instant cutoff);

    long countByStatusNot(TicketStatus status);

    /** StatsService's response-time and per-staff rollups - fetched raw, computed in Java (see DashboardController's activity chart for why). */
    List<Ticket> findByCreatedAtAfter(Instant cutoff);
}
