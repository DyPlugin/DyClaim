package dev.dyclaim.platform.proxy;
import dev.dyclaim.DyClaim;
import dev.dyclaim.platform.PlatformTasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.IOException;
import java.util.*;

/** Optional read-only diagnostic bridge; ordinary player commands remain backend-owned. */
public final class BackendStatusBridge implements PluginMessageListener,AutoCloseable {
    private final DyClaim plugin;
    private final PlatformTasks tasks;
    private final String version;
    private final StatusRequests admission=new StatusRequests(System::nanoTime);
    private volatile boolean closed;
    private boolean registered;
    public BackendStatusBridge(DyClaim plugin,PlatformTasks tasks,String version){this.plugin=plugin;this.tasks=tasks;this.version=version;}
    public void register(){
        if(closed)throw new IllegalStateException("Status bridge is closed");
        var messenger=plugin.getServer().getMessenger();messenger.registerIncomingPluginChannel(plugin,StatusProtocol.CHANNEL,this);
        messenger.registerOutgoingPluginChannel(plugin,StatusProtocol.CHANNEL);registered=true;
    }
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] bytes){
        if(closed||!StatusProtocol.CHANNEL.equals(channel))return;
        final UUID externalId;try{externalId=StatusProtocol.decodeRequest(bytes);}catch(IOException invalid){return;}
        UUID actor=player.getUniqueId();var ticket=admission.begin(actor,"backend");if(ticket.isEmpty())return;
        try{tasks.playerLater(player,()->{
            if(closed||!plugin.isEnabled()||!player.isOnline())return;
            if(!admission.complete(actor,"backend","backend",new StatusProtocol.Status(ticket.get().id(),version,Set.of())))return;
            if(!player.hasPermission("dyclaim.admin"))return;
            Set<String> capabilities=new HashSet<>(Set.of("claim","trust","protection","settings","teleport","en-tr"));
            if(!plugin.getClaimManager().isWritable())capabilities.add("read-only");
            player.sendPluginMessage(plugin,StatusProtocol.CHANNEL,StatusProtocol.response(new StatusProtocol.Status(externalId,version,capabilities)));
        },1);}catch(RuntimeException failure){admission.discard(actor,ticket.get().id());throw failure;}
    }
    @Override public void close(){
        closed=true;admission.clear();if(registered){registered=false;var messenger=plugin.getServer().getMessenger();messenger.unregisterIncomingPluginChannel(plugin,StatusProtocol.CHANNEL,this);messenger.unregisterOutgoingPluginChannel(plugin,StatusProtocol.CHANNEL);}
    }
}
