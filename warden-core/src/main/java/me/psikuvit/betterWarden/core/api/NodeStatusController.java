package me.psikuvit.betterWarden.core.api;

import me.psikuvit.betterWarden.core.ws.NodeWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lets a CLIENT-mode proxy (no local NodeWebSocketHandler of its own) ask the core it's connected to for /warden nodes. */
@RestController
@RequestMapping("/api/v1/nodes")
public class NodeStatusController {

    private final NodeWebSocketHandler nodeHub;

    public NodeStatusController(NodeWebSocketHandler nodeHub) {
        this.nodeHub = nodeHub;
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("connected", nodeHub.connectedCount());
    }
}
