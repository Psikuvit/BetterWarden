package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    List<Escalation> findByGroupKeyOrderByOffenceNumberAsc(String groupKey);
}
