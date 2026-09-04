package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import net.dv8tion.jda.api.entities.Member;

/**
 * Flat staff-role allowlist for MVP - spec's full Discord-role -> panel-role tiered mapping
 * (docs/spec/05-DISCORD-BOT.txt §1 "Role picker for staff permissions") is deferred, see PLAN.md.
 * An empty allowlist means nobody passes, not "everyone passes" - safer default for an
 * unconfigured bot than accidentally granting moderation commands to every member.
 */
public final class DiscordPermissions {

    private DiscordPermissions() {
    }

    public static boolean isStaff(CoreConfig.Discord config, Member member) {
        var staffRoleIds = config.getStaffRoleIds();
        if (staffRoleIds.isEmpty()) {
            return false;
        }
        return member.getRoles().stream().anyMatch(role -> staffRoleIds.contains(role.getId()));
    }
}
