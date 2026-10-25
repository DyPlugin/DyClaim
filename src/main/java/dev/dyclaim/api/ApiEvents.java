package dev.dyclaim.api;

import dev.dyclaim.DyClaim;
import dev.dyclaim.api.event.*;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;
import java.util.function.Supplier;

/** Internal: DyClaim calls these after committing a change. Not part of the supported API. */
public final class ApiEvents {
    private ApiEvents() {}
    public static void created(DyClaim plugin,ClaimData claim,Player player,ClaimCreateEvent.Cause cause){
        fire(plugin,ClaimCreateEvent.getHandlerList(),()->new ClaimCreateEvent(ClaimView.of(claim),player,cause));
    }
    public static void removed(DyClaim plugin,ClaimData claim){fire(plugin,ClaimRemoveEvent.getHandlerList(),()->new ClaimRemoveEvent(ClaimView.of(claim)));}
    public static void transferred(DyClaim plugin,ClaimData claim,UUID previous,UUID next){
        fire(plugin,ClaimTransferEvent.getHandlerList(),()->new ClaimTransferEvent(ClaimView.of(claim),previous,next));
    }
    public static void trust(DyClaim plugin,ClaimData claim,UUID player,boolean trusted){
        fire(plugin,ClaimTrustChangeEvent.getHandlerList(),()->new ClaimTrustChangeEvent(ClaimView.of(claim),player,trusted));
    }
    /** Snapshots are taken only when someone listens; delivery stays on a server thread. */
    private static void fire(DyClaim plugin,HandlerList handlers,Supplier<Event> event){
        if(plugin==null||handlers.getRegisteredListeners().length==0)return;
        Event built=event.get();
        if(Bukkit.isPrimaryThread()){Bukkit.getPluginManager().callEvent(built);return;}
        if(plugin.isEnabled())plugin.getPlatformTasks().globalLater(()->Bukkit.getPluginManager().callEvent(built),1);
    }
}
