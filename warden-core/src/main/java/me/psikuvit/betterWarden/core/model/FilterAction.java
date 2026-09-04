package me.psikuvit.betterWarden.core.model;

/** What the chat filter did with a message - always logged, never a silent delete (spec 01-CORE.txt ChatFilterService). */
public enum FilterAction {
    /** Let through, logged for staff review (ad detection, caps/spam/repeat throttle). */
    FLAGGED,
    /** Message cancelled - blocked-word hit. */
    BLOCKED
}
