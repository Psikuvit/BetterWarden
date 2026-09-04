package me.psikuvit.betterWarden.core.panel.auth;

import me.psikuvit.betterWarden.core.model.PanelUser;

public record PanelUserView(String username, String role, boolean mustChangePassword) {

    public static PanelUserView of(PanelUser u) {
        return new PanelUserView(u.getUsername(), u.getRole().name(), u.isMustChangePassword());
    }
}
