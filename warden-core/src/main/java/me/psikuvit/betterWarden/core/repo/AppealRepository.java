package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Appeal;
import me.psikuvit.betterWarden.core.model.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    List<Appeal> findAllByOrderByCreatedAtDesc();

    List<Appeal> findByStatusOrderByCreatedAtDesc(AppealStatus status);

    long countByStatus(AppealStatus status);
}
