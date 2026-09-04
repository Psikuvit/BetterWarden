package me.psikuvit.betterWarden.client;

import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.NodeEvent;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.network.dto.RevokeRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * CLIENT mode's connection to the proxy/standalone core: REST for snapshot fetch and issuing/
 * revoking punishments, a plain JDK WebSocket for live push (no extra dependency - java.net.http
 * has had WebSocket support since 11). Reconnects with capped exponential backoff and replays
 * the WriteJournal once back online.
 */
public class RemoteCoreClient {

    private static final String TOKEN_HEADER = "X-Node-Token";
    private static final long MAX_BACKOFF_SECONDS = 30;

    private final String baseUrl;
    private final String nodeToken;
    private final RemotePunishmentCache cache;
    private final WriteJournal journal;
    private final Logger logger;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "BetterWarden-RemoteCoreClient"));

    private volatile WebSocket webSocket;
    private volatile boolean stopped;
    private volatile long backoffSeconds = 1;

    public RemoteCoreClient(String baseUrl, String nodeToken, RemotePunishmentCache cache,
                             WriteJournal journal, Logger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.nodeToken = nodeToken;
        this.cache = cache;
        this.journal = journal;
        this.logger = logger;
        this.mapper = journal.mapper();
    }

    public void start() {
        stopped = false;
        fetchSnapshot();
        connectWebSocket();
    }

    public void stop() {
        stopped = true;
        scheduler.shutdownNow();
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        http.close();
    }

    private void fetchSnapshot() {
        try {
            HttpResponse<String> response = get("/api/v1/punishments/active");
            if (response.statusCode() == 200) {
                List<PunishmentDto> all = mapper.readValue(response.body(), new TypeReference<List<PunishmentDto>>() {
                });
                cache.replaceAll(all);
                logger.info("Fetched " + all.size() + " active punishment(s) from Core.");
            } else {
                logger.severe("Core snapshot fetch failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logger.severe("Could not reach Core for initial snapshot - punishment checks will be empty until reconnected: " + e.getMessage());
        }
    }

    private void fetchUuid(String uuid) {
        try {
            HttpResponse<String> response = get("/api/v1/punishments/by-uuid/" + uuid);
            if (response.statusCode() == 200) {
                List<PunishmentDto> forUuid = mapper.readValue(response.body(), new TypeReference<List<PunishmentDto>>() {
                });
                cache.put(uuid, forUuid);
            }
        } catch (Exception e) {
            logger.warning("Could not refresh punishments for " + uuid + ": " + e.getMessage());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(TOKEN_HEADER, nodeToken)
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Returns the created punishment, or empty if Core is unreachable right now - in which case
     * this also journals it. Only call this for a NEW write (a staff command); journal replay
     * uses tryIssue() instead so a still-failing replay doesn't append a duplicate entry.
     */
    public Optional<PunishmentDto> issue(IssuePunishmentRequest req) {
        Optional<PunishmentDto> result = tryIssue(req);
        if (result.isEmpty()) {
            journal.append("ISSUE", req);
        }
        return result;
    }

    public boolean revoke(Long punishmentId, RevokeRequest req) {
        boolean ok = tryRevoke(punishmentId, req);
        if (!ok) {
            journal.append("REVOKE:" + punishmentId, req);
        }
        return ok;
    }

    /** Single attempt, no journaling on failure - used by both issue() and journal replay. */
    private Optional<PunishmentDto> tryIssue(IssuePunishmentRequest req) {
        try {
            String body = mapper.writeValueAsString(req);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/punishments"))
                    .header(TOKEN_HEADER, nodeToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                PunishmentDto dto = mapper.readValue(response.body(), PunishmentDto.class);
                cache.put(dto.uuid(), List.of(dto));
                return Optional.of(dto);
            }
            logger.severe("Core rejected punishment issue: HTTP " + response.statusCode());
        } catch (Exception e) {
            logger.warning("Core unreachable issuing punishment: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** Single attempt, no journaling on failure - used by both revoke() and journal replay. */
    private boolean tryRevoke(Long punishmentId, RevokeRequest req) {
        try {
            String body = mapper.writeValueAsString(req);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/punishments/" + punishmentId + "/revoke"))
                    .header(TOKEN_HEADER, nodeToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.warning("Core unreachable revoking punishment: " + e.getMessage());
            return false;
        }
    }

    private void connectWebSocket() {
        if (stopped) {
            return;
        }
        String wsUrl = baseUrl.replaceFirst("^http", "ws") + "/ws/nodes";
        http.newWebSocketBuilder()
                .header(TOKEN_HEADER, nodeToken)
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            handleMessage(buffer.toString());
                            buffer.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        logger.warning("Lost connection to Core (WS closed: " + statusCode + " " + reason + "), reconnecting...");
                        scheduleReconnect();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        logger.warning("Core WebSocket error: " + error.getMessage());
                        scheduleReconnect();
                    }
                })
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        logger.warning("Could not connect to Core's WebSocket, retrying: " + error.getMessage());
                        scheduleReconnect();
                        return;
                    }
                    webSocket = ws;
                    backoffSeconds = 1;
                    logger.info("Connected to Core's live sync.");
                    replayJournal();
                });
    }

    private void handleMessage(String json) {
        try {
            NodeEvent event = mapper.readValue(json, NodeEvent.class);
            if (NodeEvent.PUNISHMENT_CHANGED.equals(event.event())) {
                fetchUuid(event.uuid());
            }
        } catch (Exception e) {
            logger.warning("Malformed node event from Core: " + e.getMessage());
        }
    }

    private void scheduleReconnect() {
        if (stopped) {
            return;
        }
        long delay = backoffSeconds;
        backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
        scheduler.schedule(() -> {
            fetchSnapshot();
            connectWebSocket();
        }, delay, TimeUnit.SECONDS);
    }

    /** Called once reconnected - resends whatever queued up while Core was unreachable, oldest first. */
    private void replayJournal() {
        List<WriteJournal.Entry> entries = journal.readAll();
        if (entries.isEmpty()) {
            return;
        }
        logger.info("Replaying " + entries.size() + " journaled write(s)...");
        List<WriteJournal.Entry> remaining = new ArrayList<>(entries);
        for (WriteJournal.Entry entry : entries) {
            boolean ok = replayEntry(entry);
            if (ok) {
                remaining.remove(entry);
                journal.retain(remaining);
            } else {
                logger.warning("Journal replay stopped - Core rejected or is unreachable again.");
                break;
            }
        }
    }

    private boolean replayEntry(WriteJournal.Entry entry) {
        try {
            if ("ISSUE".equals(entry.kind())) {
                IssuePunishmentRequest req = mapper.readValue(entry.payloadJson(), IssuePunishmentRequest.class);
                return tryIssue(req).isPresent();
            } else if (entry.kind().startsWith("REVOKE:")) {
                Long id = Long.parseLong(entry.kind().substring("REVOKE:".length()));
                RevokeRequest req = mapper.readValue(entry.payloadJson(), RevokeRequest.class);
                return tryRevoke(id, req);
            }
        } catch (Exception e) {
            logger.severe("Could not replay journal entry (" + entry.kind() + "): " + e.getMessage());
        }
        return false;
    }
}
