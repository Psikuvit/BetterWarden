package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.config.EditionService;
import me.psikuvit.betterWarden.core.model.IpHistoryEntry;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.repo.IpHistoryRepository;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** IP-hash correlation only (docs/spec/01-CORE.txt §4) - no shared-IP whitelist yet. */
@Service
public class AltDetectionService {

    private final EditionService edition;
    private final IpHistoryRepository ipHistory;
    private final PlayerRepository players;

    public AltDetectionService(EditionService edition, IpHistoryRepository ipHistory, PlayerRepository players) {
        this.edition = edition;
        this.ipHistory = ipHistory;
        this.players = players;
    }

    /** docs/spec/08-TIERS-AND-LICENSING.txt - paid-only; free edition always reports no alts (also means ban-evasion-auto-action is a no-op in free, since it acts on this result). */
    public List<Player> findAlts(UUID uuid) {
        if (edition.isFree()) {
            return List.of();
        }
        Set<String> hashes = new LinkedHashSet<>();
        for (IpHistoryEntry entry : ipHistory.findByUuid(uuid.toString())) {
            hashes.add(entry.getIpHash());
        }

        Set<String> altUuids = new LinkedHashSet<>();
        for (String hash : hashes) {
            for (IpHistoryEntry other : ipHistory.findByIpHashAndUuidNot(hash, uuid.toString())) {
                altUuids.add(other.getUuid());
            }
        }

        return altUuids.stream()
                .map(players::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
