package com.dogra.agent;

public final class Command {
    public enum Kind { OPEN, SEARCH, YOUTUBE, MAPS, BACK, HOME, RECENTS, NOTIFICATIONS,
        QUICK_SETTINGS, SCROLL, TAP, TYPE, READ, NUMBERS, HIDE_NUMBERS, VOLUME,
        TIMER, DIAL, TIME, HELP, STOP, UNKNOWN }
    public final Kind kind;
    public final String value;
    public Command(Kind kind, String value) { this.kind = kind; this.value = value; }
    public Command(Kind kind) { this(kind, ""); }
}
