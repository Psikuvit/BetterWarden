package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.NameHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NameHistoryRepository extends JpaRepository<NameHistoryEntry, Long> {

    List<NameHistoryEntry> findByUuidOrderBySeenAtDesc(String uuid);
}
