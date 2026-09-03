package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.StaffNote;
import me.psikuvit.betterWarden.core.repo.StaffNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StaffNoteService {

    private final StaffNoteRepository notes;

    public StaffNoteService(StaffNoteRepository notes) {
        this.notes = notes;
    }

    @Transactional
    public StaffNote add(UUID targetUuid, UUID staffUuid, String body) {
        StaffNote note = new StaffNote();
        note.setTargetUuid(targetUuid.toString());
        note.setStaffUuid(staffUuid == null ? "CONSOLE" : staffUuid.toString());
        note.setBody(body);
        note.setCreatedAt(Instant.now());
        return notes.save(note);
    }

    public List<StaffNote> list(UUID targetUuid) {
        return notes.findByTargetUuidOrderByCreatedAtDesc(targetUuid.toString());
    }
}
