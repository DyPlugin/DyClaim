package dev.dyclaim.bungeecord;
import dev.dyclaim.platform.proxy.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.event.EventHandler;
import java.util.function.*;

public final class BungeeMessages implements Listener {
    public record Reply(String backend,StatusProtocol.Status status) {}
    private final StatusRequests requests;
    private final BooleanSupplier alive;
    private final BiConsumer<ProxiedPlayer,Reply> sink;
    public BungeeMessages(StatusRequests requests,BooleanSupplier alive,BiConsumer<ProxiedPlayer,Reply> sink){this.requests=requests;this.alive=alive;this.sink=sink;}
    @EventHandler public void onMessage(PluginMessageEvent event){
        if(!StatusProtocol.CHANNEL.equals(event.getTag()))return;
        event.setCancelled(true);
        if(!alive.getAsBoolean()||!(event.getSender() instanceof Server source)||!(event.getReceiver() instanceof ProxiedPlayer player))return;
        Server current=player.getServer();
        if(!player.hasPermission("dyclaim.proxy.status")||current==null){requests.remove(player.getUniqueId());return;}
        final StatusProtocol.Status status;try{status=StatusProtocol.decodeStatus(event.getData());}catch(java.io.IOException invalid){return;}
        String backend=current.getInfo().getName();
        if(requests.complete(player.getUniqueId(),source.getInfo().getName(),backend,status))sink.accept(player,new Reply(backend,status));
    }
}
