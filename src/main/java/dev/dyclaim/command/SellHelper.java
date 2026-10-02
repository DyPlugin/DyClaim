package dev.dyclaim.command;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import java.util.UUID;
import org.bukkit.entity.Player;
import java.util.Map;

public final class SellHelper {
    private SellHelper() {}
    public static void request(DyClaim plugin,Player player,boolean unclaim) {
        if(!player.hasPermission("dyclaim.sell")){send(plugin,player,"no-permission");return;}
        ClaimData claim=plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        if(claim==null){send(plugin,player,"sell-not-claimed");return;}
        if(!claim.getOwnerUUID().equals(player.getUniqueId())&&!player.hasPermission("dyclaim.admin")){send(plugin,player,"sell-not-owner");return;}
        if(plugin.getTransactionManager().isLocked(claim.getChunkKey())){send(plugin,player,"transaction-locked");return;}
        double refund=plugin.getClaimManager().refund(claim);
        UUID owner=claim.getOwnerUUID(),id=claim.getId();long revision=claim.getRevision();String key=claim.getChunkKey();
        player.sendMessage(plugin.getMessageManager().getPrefixed(player,"sell-confirmation",Map.of("{refund}",plugin.getEconomyManager().formatMoney(refund))));
        Runnable execute=()->{
            ClaimData current=plugin.getClaimManager().getByKey(key);
            if(!player.isOnline()||!player.hasPermission("dyclaim.sell")||current==null||!current.getId().equals(id)||current.getRevision()!=revision
                    ||!current.getOwnerUUID().equals(owner)||(!owner.equals(player.getUniqueId())&&!player.hasPermission("dyclaim.admin"))
                    ||Double.compare(refund,plugin.getClaimManager().refund(current))!=0){send(plugin,player,"operation-changed");return;}
            if(plugin.getClaimManager().refundAndRemove(current))player.sendMessage(plugin.getMessageManager().getPrefixed(player,"sell-success",Map.of("{refund}",plugin.getEconomyManager().formatMoney(refund))));
            else send(plugin,player,"transaction-failed");
        };
        if(plugin.getConfigManager().values().getBoolean("confirmations.required."+(unclaim?"unclaim":"refund-sale"),true))
            plugin.getConfirmationManager().addPending(player.getUniqueId(),dev.dyclaim.manager.ConfirmationManager.ActionType.SELL,key,uuid->execute.run(),uuid->send(plugin,player,"confirm-deny"));
        else execute.run();
    }
    private static void send(DyClaim plugin,Player player,String key){player.sendMessage(plugin.getMessageManager().getPrefixed(player,key));}
}
