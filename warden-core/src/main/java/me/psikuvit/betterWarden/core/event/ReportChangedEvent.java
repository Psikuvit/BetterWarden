package me.psikuvit.betterWarden.core.event;

import me.psikuvit.betterWarden.core.model.Report;

/** Published on claim/dismiss/close, from whichever surface did it (in-game, panel, or a Discord button) - lets the Discord #reports feed keep its embed in sync regardless of origin. */
public record ReportChangedEvent(Report report) {
}
