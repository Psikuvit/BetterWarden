package me.psikuvit.betterWarden.core.client;

import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * Append-only local log for CLIENT-mode writes that couldn't reach Core (network blip, Core
 * restarting). Replayed on reconnect by RemoteCoreClient, one line removed per successful send -
 * "best-effort idempotent": a crash between a successful send and the file rewrite could resend
 * an already-applied entry. Not a real WAL, just enough that a staff action taken while Core was
 * briefly unreachable isn't silently lost.
 */
public class WriteJournal {

    public record Entry(String kind, String payloadJson) {
    }

    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger;

    public WriteJournal(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "write-journal.jsonl");
        this.logger = logger;
    }

    public synchronized void append(String kind, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            Files.writeString(file.toPath(), kind + "\t" + json + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            logger.error("Could not write to the write-journal - this action may be lost: {}", e.getMessage());
        }
    }

    public synchronized List<Entry> readAll() {
        List<Entry> entries = new ArrayList<>();
        if (!file.exists()) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                entries.add(new Entry(line.substring(0, tab), line.substring(tab + 1)));
            }
        } catch (IOException e) {
            logger.error("Could not read write-journal: {}", e.getMessage());
        }
        return entries;
    }

    /** Rewrites the journal with whatever wasn't successfully replayed, in order. */
    public synchronized void retain(List<Entry> remaining) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Entry e : remaining) {
                sb.append(e.kind()).append('\t').append(e.payloadJson()).append(System.lineSeparator());
            }
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Could not rewrite write-journal after replay: {}", e.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
