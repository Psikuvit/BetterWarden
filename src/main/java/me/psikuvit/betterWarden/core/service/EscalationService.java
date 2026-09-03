package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.Escalation;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.EscalationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EscalationService {

    private final EscalationRepository escalations;

    public EscalationService(EscalationRepository escalations) {
        this.escalations = escalations;
    }

    @Transactional
    public Escalation addRung(String groupKey, int offenceNumber, PunishmentType type, String duration) {
        Escalation rung = new Escalation();
        rung.setGroupKey(groupKey);
        rung.setOffenceNumber(offenceNumber);
        rung.setType(type);
        rung.setDuration(duration);
        return escalations.save(rung);
    }

    public List<Escalation> list(String groupKey) {
        return escalations.findByGroupKeyOrderByOffenceNumberAsc(groupKey);
    }
}
