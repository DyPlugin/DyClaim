package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.function.Consumer;

public class ConfirmationManager {
    private final DyClaim plugin;
    private final Map<UUID,PendingAction> pending=new HashMap<>();
    public ConfirmationManager(DyClaim plugin){this.plugin=plugin;}
    public enum ActionType { CLAIM, SELL, TRANSFER, COOWNER, MARKET, PURGE, ADMIN }
    public static class PendingAction {
        final String token=UUID.randomUUID().toString();
        final ActionType type;
        final String key;
        final long expires;
        final Consumer<UUID> confirm,deny;
        BukkitTask task;
        PendingAction(ActionType type,String key,long expires,Consumer<UUID> confirm,Consumer<UUID> deny){this.type=type;this.key=key;this.expires=expires;this.confirm=confirm;this.deny=deny;}
    }
    public void addPending(UUID uuid,ActionType type,Consumer<UUID> confirm,Consumer<UUID> deny){addPending(uuid,type,null,confirm,deny);}
    public void addPending(UUID uuid,ActionType type,String key,Consumer<UUID> confirm,Consumer<UUID> deny) {
        boolean replacing=hasPending(uuid);cancelPending(uuid);
        Player player=plugin.getServer().getPlayer(uuid);
        if(replacing&&player!=null)player.sendMessage(plugin.getMessageManager().getPrefixed(player,"confirm-replaced"));
        long seconds=plugin.getConfigManager().values().getLong("confirmations.timeout-seconds",plugin.getConfigManager().getConfirmationTimeout());
        PendingAction action=new PendingAction(type,key,System.currentTimeMillis()+seconds*1000,confirm,deny);
        pending.put(uuid,action);
        action.task=plugin.getServer().getScheduler().runTaskLater(plugin,()->{
            if(pending.get(uuid)==action){cancelPending(uuid);Player p=plugin.getServer().getPlayer(uuid);if(p!=null)p.sendMessage(plugin.getMessageManager().getPrefixed(p,"confirm-expired"));}
        },seconds*20);
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
        cancelPending(uuid);
        if(System.currentTimeMillis()>=action.expires)return false;
        action.confirm.accept(uuid);return true;
    }
    public boolean deny(UUID uuid){return deny(uuid,null);}
    public boolean deny(UUID uuid,String token){
        PendingAction action=pending.get(uuid);if(action==null||(token!=null&&!action.token.equals(token)))return false;
        cancelPending(uuid);action.deny.accept(uuid);return true;
    }
    public boolean hasPending(UUID uuid){PendingAction action=pending.get(uuid);return action!=null&&action.expires>System.currentTimeMillis();}
    public void cancelPending(UUID uuid){PendingAction action=pending.remove(uuid);if(action!=null&&action.task!=null)action.task.cancel();}
    public void cancelClaim(String key){for(UUID uuid:new HashSet<>(pending.keySet()))if(Objects.equals(key,pending.get(uuid).key))cancelPending(uuid);}
    public void clear(){for(UUID uuid:new HashSet<>(pending.keySet()))cancelPending(uuid);}
}
