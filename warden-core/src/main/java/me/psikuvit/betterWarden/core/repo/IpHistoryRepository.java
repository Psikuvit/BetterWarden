package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.IpHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IpHistoryRepository extends JpaRepository<IpHistoryEntry, Long> {

    List<IpHistoryEntry> findByUuid(String uuid);

    List<IpHistoryEntry> findByIpHashAndUuidNot(String ipHash, String uuid);
}
