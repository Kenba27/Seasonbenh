package vn.seasonsdiseases;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Phat ra khi nguoi choi khoi benh (chua hoac tu khoi). */
public class DiseaseCureEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Disease disease;
    private final String reason;

    public DiseaseCureEvent(Player who, Disease disease, String reason) {
        super(who);
        this.disease = disease;
        this.reason = reason;
    }

    public Disease getDisease() {
        return disease;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
