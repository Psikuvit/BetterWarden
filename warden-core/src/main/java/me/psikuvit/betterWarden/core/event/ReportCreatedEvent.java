package me.psikuvit.betterWarden.core.event;

import me.psikuvit.betterWarden.core.model.Report;

/** Published whenever a new report is filed - the Discord #reports feed (docs/spec/05-DISCORD-BOT.txt §3/§4) is the first real consumer. */
public record ReportCreatedEvent(Report report) {
}
