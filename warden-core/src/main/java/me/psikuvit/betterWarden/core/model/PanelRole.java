package me.psikuvit.betterWarden.core.model;

/** docs/spec/04-PANEL.txt §3. Not yet enforced per-endpoint - stored and returned, but every
 * panel endpoint so far only checks "authenticated or not". Real per-role authorization is
 * still open (PLAN.md Stage 5). */
public enum PanelRole {
    OWNER,
    ADMIN,
    MODERATOR,
    VIEWER
}
