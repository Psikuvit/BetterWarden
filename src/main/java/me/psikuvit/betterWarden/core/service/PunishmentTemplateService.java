package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PunishmentTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PunishmentTemplateService {

    private final PunishmentTemplateRepository templates;

    public PunishmentTemplateService(PunishmentTemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public PunishmentTemplate create(String key, String display, PunishmentType type, String duration, String reason) {
        PunishmentTemplate template = new PunishmentTemplate();
        template.setKey(key);
        template.setDisplay(display);
        template.setType(type);
        template.setDuration(duration);
        template.setReason(reason);
        return templates.save(template);
    }

    public Optional<PunishmentTemplate> find(String key) {
        return templates.findByKeyIgnoreCase(key);
    }

    public List<PunishmentTemplate> list() {
        return templates.findAll();
    }

    @Transactional
    public boolean delete(String key) {
        Optional<PunishmentTemplate> template = templates.findByKeyIgnoreCase(key);
        template.ifPresent(templates::delete);
        return template.isPresent();
    }
}
