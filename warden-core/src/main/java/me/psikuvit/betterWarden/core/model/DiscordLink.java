package me.psikuvit.betterWarden.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** docs/spec/05-DISCORD-BOT.txt §6 - one row per linked Discord account. discordUserId is the Discord snowflake, kept as the primary key since a Discord account can only ever link to one Minecraft account at a time; minecraftUuid is unique for the same reason in reverse. */
@Entity
@Table(name = "discord_links")
public class DiscordLink {

    @Id
    @Column(name = "discord_user_id", length = 32)
    private String discordUserId;

    @Column(name = "minecraft_uuid", nullable = false, length = 36, unique = true)
    private String minecraftUuid;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    public DiscordLink() {
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public void setDiscordUserId(String discordUserId) {
        this.discordUserId = discordUserId;
    }

    public String getMinecraftUuid() {
        return minecraftUuid;
    }

    public void setMinecraftUuid(String minecraftUuid) {
        this.minecraftUuid = minecraftUuid;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }
}
