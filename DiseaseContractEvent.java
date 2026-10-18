package vn.seasonsdiseases;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Phat ra khi nguoi choi sap mac benh. Co the huy (cancel). */
public class DiseaseContractEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Disease disease;
    private final boolean contagion;
    private boolean cancelled;

    public DiseaseContractEvent(Player who, Disease disease, boolean contagion) {
        super(who);
        this.disease = disease;
        this.contagion = contagion;
    }

    public Disease getDisease() {
        return disease;
    }

    /** true neu bi lay tu nguoi choi khac. */
    public boolean isContagion() {
        return contagion;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
