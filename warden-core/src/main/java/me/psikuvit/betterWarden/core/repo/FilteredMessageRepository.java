package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.FilteredMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FilteredMessageRepository extends JpaRepository<FilteredMessage, Long> {

    List<FilteredMessage> findByReviewedFalseOrderByCreatedAtDesc();

    long countByPlayerUuidAndCreatedAtAfter(String playerUuid, Instant after);
}
