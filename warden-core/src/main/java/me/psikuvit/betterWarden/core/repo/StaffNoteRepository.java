package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.StaffNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffNoteRepository extends JpaRepository<StaffNote, Long> {

    List<StaffNote> findByTargetUuidOrderByCreatedAtDesc(String targetUuid);
}
