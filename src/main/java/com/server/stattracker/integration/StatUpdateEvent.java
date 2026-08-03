package com.server.stattracker.integration;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class StatUpdateEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final String statKey;
    private final long newValue;

    public StatUpdateEvent(Player player, String statKey, long newValue) {
        this.player = player;
        this.statKey = statKey;
        this.newValue = newValue;
    }

    public Player getPlayer() { return player; }

        public String getStatKey() { return statKey; }

        public long getNewValue() { return newValue; }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
