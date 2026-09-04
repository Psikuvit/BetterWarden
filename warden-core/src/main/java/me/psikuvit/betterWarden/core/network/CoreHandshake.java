package me.psikuvit.betterWarden.core.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Wire payload sent over the {@code warden:core} plugin channel when a proxy
 * announces its embedded core to a backend server (docs/spec Stage 3). Kept
 * dependency-free (no Jackson) since the receiving side may not have Spring
 * booted yet when this arrives.
 */
public record CoreHandshake(String coreUrl, String nodeToken, String version, String tier) {

    public static final String CHANNEL_NAMESPACE = "warden";
    public static final String CHANNEL_NAME = "core";
    /** As Bukkit's Messenger expects it: {@code "<namespace>:<name>"}. */
    public static final String CHANNEL_ID = CHANNEL_NAMESPACE + ":" + CHANNEL_NAME;

    /** Embedded in a proxy/standalone process, HOST-mode core (Stage 3/4 - see PLAN.md). */
    public static final String TIER_EMBEDDED = "EMBEDDED";
    public static final String TIER_STANDALONE = "STANDALONE";

    private static final int PROTOCOL_VERSION = 1;

    public byte[] toBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(PROTOCOL_VERSION);
            out.writeUTF(coreUrl);
            out.writeUTF(nodeToken);
            out.writeUTF(version);
            out.writeUTF(tier);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CoreHandshake fromBytes(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int protocolVersion = in.readInt();
            if (protocolVersion != PROTOCOL_VERSION) {
                throw new IOException("Unsupported handshake protocol version " + protocolVersion
                        + " (expected " + PROTOCOL_VERSION + ") - proxy and backend plugin versions likely mismatch");
            }
            return new CoreHandshake(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
        }
    }
}
