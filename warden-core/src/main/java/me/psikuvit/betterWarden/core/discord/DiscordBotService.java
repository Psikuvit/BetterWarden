package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * docs/spec/05-DISCORD-BOT.txt: "JDA, running inside Core", buyer's own bot token, entirely
 * optional. Bot failure must never affect the game server (spec §8) - every path here either
 * logs and disables, or logs and continues; nothing here throws back into the boot sequence.
 * Connects asynchronously (JDABuilder#build() returns before the gateway handshake completes) so
 * a slow or unreachable Discord doesn't hold up the rest of the application starting.
 */
@Service
public class DiscordBotService extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotService.class);

    private final CoreConfig config;
    private final ModerationSlashCommands moderationSlashCommands;
    private volatile JDA jda;

    public DiscordBotService(CoreConfig config, ModerationSlashCommands moderationSlashCommands) {
        this.config = config;
        this.moderationSlashCommands = moderationSlashCommands;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        CoreConfig.Discord discord = config.getDiscord();
        if (!discord.isEnabled() || discord.getToken().isBlank()) {
            log.info("Discord bot disabled (no token configured) - see config.yml warden.discord.");
            return;
        }
        try {
            jda = JDABuilder.createLight(discord.getToken())
                    .addEventListeners(this, moderationSlashCommands)
                    .build();
        } catch (Exception e) {
            // A malformed token (wrong length/format) fails build() synchronously with an
            // unchecked InvalidTokenException - confirmed by actually booting against one, not
            // assumed. A validly-formatted but wrong/revoked token instead connects, gets
            // rejected by Discord's own gateway, and is caught by onShutdown() below instead.
            log.error("Could not start the Discord bot - bot disabled, everything else continues", e);
        }
    }

    /** Covers a validly-formatted token that Discord itself rejects (wrong/revoked) - the other failure mode, see the comment in connect() above. */
    @Override
    public void onShutdown(ShutdownEvent event) {
        if (event.getCloseCode() == CloseCode.AUTHENTICATION_FAILED) {
            log.error("Discord bot token was rejected by Discord - check warden.discord.token in config.yml. Everything else continues.");
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = resolveGuild();
        if (guild == null) {
            log.warn("Discord bot connected as {}, but warden.discord.guild-id is not set or doesn't "
                    + "match a guild it's in - slash commands were not registered. Invite it to your "
                    + "server and set guild-id, then restart.", event.getJDA().getSelfUser().getAsTag());
            return;
        }
        log.info("Discord bot connected as {} in guild '{}'.", event.getJDA().getSelfUser().getAsTag(), guild.getName());
        moderationSlashCommands.registerCommands(guild);
    }

    @EventListener(ContextClosedEvent.class)
    public void disconnect() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    /** Null until connected, or if warden.discord.guild-id isn't set / doesn't match a guild the bot is actually in. */
    public Guild resolveGuild() {
        if (jda == null) {
            return null;
        }
        String guildId = config.getDiscord().getGuildId();
        return guildId.isBlank() ? null : jda.getGuildById(guildId);
    }

    /** Null if not connected, no guild resolved, channelId is blank (feed disabled), or the channel doesn't exist. */
    public TextChannel resolveChannel(String channelId) {
        Guild guild = resolveGuild();
        if (guild == null || channelId == null || channelId.isBlank()) {
            return null;
        }
        return guild.getTextChannelById(channelId);
    }
}
