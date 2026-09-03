package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findByTicketIdOrderBySentAtAsc(Long ticketId);
}
