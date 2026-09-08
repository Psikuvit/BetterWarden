package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.config.EditionService;
import me.psikuvit.betterWarden.core.model.StaffNote;
import me.psikuvit.betterWarden.core.repo.StaffNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** docs/spec/08-TIERS-AND-LICENSING.txt - paid-only. Returns Optional/empty in the free edition rather than silently pretending to save, so callers can show an honest "paid feature" message instead of a false "note added" confirmation. */
@Service
public class StaffNoteService {

    private final EditionService edition;
    private final StaffNoteRepository notes;

    public StaffNoteService(EditionService edition, StaffNoteRepository notes) {
        this.edition = edition;
        this.notes = notes;
    }

    @Transactional
    public Optional<StaffNote> add(UUID targetUuid, UUID staffUuid, String body) {
        if (edition.isFree()) {
            return Optional.empty();
        }
        StaffNote note = new StaffNote();
        note.setTargetUuid(targetUuid.toString());
        note.setStaffUuid(staffUuid == null ? "CONSOLE" : staffUuid.toString());
        note.setBody(body);
        note.setCreatedAt(Instant.now());
        return Optional.of(notes.save(note));
    }

    public List<StaffNote> list(UUID targetUuid) {
        if (edition.isFree()) {
            return List.of();
        }
        return notes.findByTargetUuidOrderByCreatedAtDesc(targetUuid.toString());
    }
}
