package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.IpHistoryEntry;
import me.psikuvit.betterWarden.core.model.NameHistoryEntry;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.repo.IpHistoryRepository;
import me.psikuvit.betterWarden.core.repo.NameHistoryRepository;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlayerTrackingService {

    private final PlayerRepository players;
    private final NameHistoryRepository nameHistory;
    private final IpHistoryRepository ipHistory;
    private final IpHashingService ipHashing;

    public PlayerTrackingService(PlayerRepository players, NameHistoryRepository nameHistory,
                                  IpHistoryRepository ipHistory, IpHashingService ipHashing) {
        this.players = players;
        this.nameHistory = nameHistory;
        this.ipHistory = ipHistory;
        this.ipHashing = ipHashing;
    }

    @Async("wardenExecutor")
    public void trackJoinAsync(UUID uuid, String name, String rawIp) {
        trackJoin(uuid, name, rawIp);
    }

    @Transactional
    public void trackJoin(UUID uuid, String name, String rawIp) {
        String ipHash = rawIp == null ? null : ipHashing.hash(rawIp);
        Instant now = Instant.now();

        Player player = players.findById(uuid.toString()).orElse(null);
        boolean isNew = player == null;
        boolean nameChanged = isNew || !name.equals(player.getLastName());
        if (isNew) {
            player = new Player();
            player.setUuid(uuid.toString());
            player.setFirstSeen(now);
        }
        player.setLastName(name);
        player.setLastSeen(now);
        if (ipHash != null) {
            player.setLastIpHash(ipHash);
        }
        players.save(player);

        if (nameChanged) {
            NameHistoryEntry entry = new NameHistoryEntry();
            entry.setUuid(uuid.toString());
            entry.setName(name);
            entry.setSeenAt(now);
            nameHistory.save(entry);
        }

        if (ipHash != null) {
            recordIp(uuid, ipHash, now);
        }
    }

    private void recordIp(UUID uuid, String ipHash, Instant now) {
        List<IpHistoryEntry> existing = ipHistory.findByUuid(uuid.toString());
        for (IpHistoryEntry entry : existing) {
            if (entry.getIpHash().equals(ipHash)) {
                entry.setLastSeen(now);
                return;
            }
        }
        IpHistoryEntry entry = new IpHistoryEntry();
        entry.setUuid(uuid.toString());
        entry.setIpHash(ipHash);
        entry.setFirstSeen(now);
        entry.setLastSeen(now);
        ipHistory.save(entry);
    }

    @Transactional
    public void ensurePlayerExists(UUID uuid, String name) {
        if (players.existsById(uuid.toString())) {
            return;
        }
        Instant now = Instant.now();
        Player p = new Player();
        p.setUuid(uuid.toString());
        p.setLastName(name);
        p.setFirstSeen(now);
        p.setLastSeen(now);
        players.save(p);
    }

    @Async("wardenExecutor")
    @Transactional
    public void flushSessionAsync(UUID uuid) {
        players.findById(uuid.toString()).ifPresent(p -> p.setLastSeen(Instant.now()));
    }

    public Optional<String> lastIpHash(UUID uuid) {
        return players.findById(uuid.toString()).map(Player::getLastIpHash);
    }

    public Optional<Player> find(UUID uuid) {
        return players.findById(uuid.toString());
    }
}
