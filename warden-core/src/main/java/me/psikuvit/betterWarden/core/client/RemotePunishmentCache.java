package me.psikuvit.betterWarden.core.client;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** CLIENT-mode mirror of PunishmentCache/PunishmentService - same query shape, backed by RemoteCoreClient's REST snapshot + WS pushes instead of local JPA. */
public class RemotePunishmentCache implements PunishmentGateway {

    private final Map<String, List<Punishment>> byUuid = new ConcurrentHashMap<>();

    public void put(String uuid, List<PunishmentDto> dtos) {
        byUuid.put(uuid, dtos.stream().map(PunishmentDto::toEntity).toList());
    }

    public void replaceAll(List<PunishmentDto> allActive) {
        Map<String, List<Punishment>> grouped = new ConcurrentHashMap<>();
        for (PunishmentDto dto : allActive) {
            grouped.computeIfAbsent(dto.uuid(), k -> new ArrayList<>()).add(dto.toEntity());
        }
        byUuid.clear();
        byUuid.putAll(grouped);
    }

    private List<Punishment> active(String uuid) {
        return byUuid.getOrDefault(uuid, List.of());
    }

    @Override
    public Optional<Punishment> activeBan(UUID uuid) {
        return active(uuid.toString()).stream().filter(p -> PunishmentType.BAN_TYPES.contains(p.getType())).findFirst();
    }

    @Override
    public Optional<Punishment> activeMute(UUID uuid) {
        return active(uuid.toString()).stream().filter(p -> PunishmentType.MUTE_TYPES.contains(p.getType())).findFirst();
    }

    @Override
    public Optional<Punishment> activeIpBan(String ipHash) {
        return byUuid.values().stream().flatMap(List::stream)
                .filter(p -> p.getType() == PunishmentType.IPBAN && ipHash.equals(p.getIpHash()))
                .findFirst();
    }

    @Override
    public String resolveStaffName(Punishment punishment) {
        // No local player table to resolve a name against without another round-trip - the raw
        // UUID/"Console" is honest here, unlike the HOST side which has PlayerTrackingService.
        return punishment.getStaffUuid() == null ? "Console" : punishment.getStaffUuid();
    }
}
