package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentChangedEvent;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory active-punishment cache, warmed on boot and kept in sync via PunishmentChangedEvent. */
@Component
public class PunishmentCache {

    private final PunishmentRepository punishments;
    private final Map<String, List<Punishment>> byUuid = new ConcurrentHashMap<>();

    public PunishmentCache(PunishmentRepository punishments, EventBus eventBus) {
        this.punishments = punishments;
        eventBus.subscribe(PunishmentChangedEvent.class, e -> refresh(e.uuid()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        byUuid.clear();
        for (Punishment p : punishments.findByActiveTrue()) {
            byUuid.computeIfAbsent(p.getUuid(), k -> new CopyOnWriteArrayList<>()).add(p);
        }
    }

    public void refresh(String uuid) {
        byUuid.put(uuid, new CopyOnWriteArrayList<>(punishments.findByUuidAndActiveTrue(uuid)));
    }

    public List<Punishment> active(String uuid) {
        return byUuid.getOrDefault(uuid, List.of());
    }

    public Optional<Punishment> activeBan(String uuid) {
        return active(uuid).stream().filter(p -> PunishmentType.BAN_TYPES.contains(p.getType())).findFirst();
    }

    public Optional<Punishment> activeMute(String uuid) {
        return active(uuid).stream().filter(p -> PunishmentType.MUTE_TYPES.contains(p.getType())).findFirst();
    }

    public Optional<Punishment> activeIpBan(String ipHash) {
        return byUuid.values().stream()
                .flatMap(List::stream)
                .filter(p -> p.getType() == PunishmentType.IPBAN && ipHash.equals(p.getIpHash()))
                .findFirst();
    }
}
