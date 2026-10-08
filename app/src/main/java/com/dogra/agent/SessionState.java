package com.dogra.agent;

import java.util.ArrayList;
import java.util.ArrayDeque;

final class SessionState {
    static boolean active, listening;
    static String status = "Ready. Mic dabao aur command bolo.", heard = "";
    static final ArrayDeque<String> history = new ArrayDeque<>();
    static final ArrayList<Runnable> listeners = new ArrayList<>();
    static void update(String value) {
        status = value;
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
        AgentAccessibilityService access = AgentAccessibilityService.instance;
        if (access != null) access.refreshBubble();
    }
    static void record(String command) {
        heard = command;
        history.addFirst(command);
        while (history.size() > 12) history.removeLast();
    }
}
