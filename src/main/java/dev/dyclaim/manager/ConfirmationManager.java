package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.function.Consumer;

public class ConfirmationManager {
    private final DyClaim plugin;
    private final Map<UUID,PendingAction> pending=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Deferred> deferred=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    private static final class Deferred {
        final String key;
        Deferred(String key){this.key=key;}
    }
    public ConfirmationManager(DyClaim plugin){this.plugin=plugin;}
    public enum ActionType { CLAIM, SELL, TRANSFER, COOWNER, MARKET, PURGE, ADMIN }
    public static class PendingAction {
        final String token=UUID.randomUUID().toString();
        final ActionType type;
        final String key;
        final long expires;
        final Consumer<UUID> confirm,deny;
        volatile BukkitTask task;
        PendingAction(ActionType type,String key,long expires,Consumer<UUID> confirm,Consumer<UUID> deny){this.type=type;this.key=key;this.expires=expires;this.confirm=confirm;this.deny=deny;}
    }
    public void addPending(UUID uuid,ActionType type,Consumer<UUID> confirm,Consumer<UUID> deny){addPending(uuid,type,null,confirm,deny);}
    public void addPending(UUID uuid,ActionType type,String key,Consumer<UUID> confirm,Consumer<UUID> deny) {
        Player player=plugin.getServer().getPlayer(uuid);
        long epoch=generation.get();
        var tasks=plugin.getPlatformTasks();
        if(player!=null&&tasks.isRegionThreaded()&&!tasks.ownsEntity(player)){
            Deferred offer=new Deferred(key);deferred.put(uuid,offer);
            try{tasks.entityLater(player,()->{
                if(deferred.remove(uuid,offer)&&epoch==generation.get()&&player.isOnline())install(uuid,type,key,confirm,deny,player,epoch);
            },()->deferred.remove(uuid,offer),1);}catch(RuntimeException failure){deferred.remove(uuid,offer);throw failure;}
            return;
        }
        deferred.remove(uuid);
        install(uuid,type,key,confirm,deny,player,epoch);
    }
    private void install(UUID uuid,ActionType type,String key,Consumer<UUID> confirm,Consumer<UUID> deny,Player player,long epoch){
        if(epoch!=generation.get())return;
        boolean replacing=hasPending(uuid);cancelInstalled(uuid);
        if(replacing&&player!=null)player.sendMessage(plugin.getMessageManager().getPrefixed(player,"confirm-replaced"));
        long seconds=plugin.getConfigManager().values().getLong("confirmations.timeout-seconds",plugin.getConfigManager().getConfirmationTimeout());
        PendingAction action=new PendingAction(type,key,System.currentTimeMillis()+seconds*1000,confirm,deny);
        pending.put(uuid,action);
        if(epoch!=generation.get()){consume(uuid,action);return;}
        Runnable expire=()->{if(consume(uuid,action)&&player!=null&&player.isOnline())player.sendMessage(plugin.getMessageManager().getPrefixed(player,"confirm-expired"));};
        try{action.task=player==null?plugin.getPlatformTasks().globalLater(expire,seconds*20):plugin.getPlatformTasks().playerLater(player,expire,seconds*20);}
        catch(RuntimeException failure){consume(uuid,action);throw failure;}
        if(pending.get(uuid)!=action){action.task.cancel();return;}
        if(player!=null) {
            player.sendMessage(plugin.getMessageManager().getPrefixed(player,"confirm-text"));
            if(plugin.getConfigManager().values().getBoolean("confirmations.clickable-buttons",true)) {
                TextComponent accept=new TextComponent(plugin.getMessageManager().getMessage(player,"confirm-accept"));
                accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/confirm "+action.token));
                TextComponent cancel=new TextComponent("  "+plugin.getMessageManager().getMessage(player,"confirm-deny"));
                cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/cancel "+action.token));
                accept.addExtra(cancel);player.spigot().sendMessage(accept);
            }
        }
    }
    public boolean confirm(UUID uuid){return confirm(uuid,null);}
    public boolean confirm(UUID uuid,String token){
        PendingAction action=pending.get(uuid);
        if(action==null||(token!=null&&!action.token.equals(token)))return false;
        if(!consume(uuid,action))return false;
        if(System.currentTimeMillis()>=action.expires)return false;
        action.confirm.accept(uuid);return true;
    }
    public boolean deny(UUID uuid){return deny(uuid,null);}
    public boolean deny(UUID uuid,String token){
        PendingAction action=pending.get(uuid);if(action==null||(token!=null&&!action.token.equals(token)))return false;
        if(!consume(uuid,action))return false;
        action.deny.accept(uuid);return true;
    }
    public boolean hasPending(UUID uuid){PendingAction action=pending.get(uuid);return action!=null&&action.expires>System.currentTimeMillis();}
    public void cancelPending(UUID uuid){deferred.remove(uuid);cancelInstalled(uuid);}
    private void cancelInstalled(UUID uuid){PendingAction action=pending.remove(uuid);if(action!=null&&action.task!=null)action.task.cancel();}
    private boolean consume(UUID uuid,PendingAction action){if(!pending.remove(uuid,action))return false;if(action.task!=null)action.task.cancel();return true;}
    public void cancelClaim(String key){deferred.forEach((uuid,offer)->{if(Objects.equals(key,offer.key))deferred.remove(uuid,offer);});pending.forEach((uuid,action)->{if(Objects.equals(key,action.key))consume(uuid,action);});}
    public void clear(){generation.incrementAndGet();deferred.clear();for(UUID uuid:new HashSet<>(pending.keySet()))cancelInstalled(uuid);}
}
