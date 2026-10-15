package dev.dyclaim.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.dyclaim.platform.proxy.StatusProtocol;
import dev.dyclaim.platform.proxy.StatusRequests;
import dev.dyclaim.platform.proxy.BridgeVersion;
import org.slf4j.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class VelocityBridge {
    public static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(StatusProtocol.CHANNEL);
    public static final String PERMISSION="dyclaim.proxy.status";
    private static final String VERSION=BridgeVersion.get();
    private final ProxyServer proxy;
    private final Logger logger;
    private final StatusRequests requests=new StatusRequests(System::nanoTime);
    private final SimpleCommand diagnostics=new Diagnostics();
    private CommandMeta commandMeta;
    private volatile boolean closed;
    @Inject public VelocityBridge(ProxyServer proxy,Logger logger){this.proxy=proxy;this.logger=logger;}
    public SimpleCommand command(){return diagnostics;}
    @Subscribe public void initialize(ProxyInitializeEvent event){
        proxy.getChannelRegistrar().register(CHANNEL);
        if(proxy.getCommandManager().hasCommand("dyclaimproxy"))logger.warn("dyclaimproxy is already registered; existing command preserved.");
        else{commandMeta=proxy.getCommandManager().metaBuilder("dyclaimproxy").plugin(this).build();proxy.getCommandManager().register(commandMeta,diagnostics);}
        logger.info("DyClaim v{} enabled! (Velocity bridge)",VERSION);
    }
    @Subscribe public void onMessage(PluginMessageEvent event){
        if(!CHANNEL.equals(event.getIdentifier()))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(closed||!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player player))return;
        var currentConnection=player.getCurrentServer();
        if(!player.hasPermission(PERMISSION)||currentConnection.isEmpty()){requests.remove(player.getUniqueId());return;}
        final StatusProtocol.Status status;try{status=StatusProtocol.decodeStatus(event.getData());}catch(java.io.IOException invalid){return;}
        String current=currentConnection.orElseThrow().getServerInfo().getName();
        if(requests.complete(player.getUniqueId(),source.getServerInfo().getName(),current,status))send(player,"DyClaim-v"+status.version()+" · "+current+" · "+String.join(", ",new TreeSet<>(status.capabilities())));
    }
    @Subscribe public void switched(ServerPostConnectEvent event){requests.remove(event.getPlayer().getUniqueId());}
    @Subscribe public void disconnected(DisconnectEvent event){requests.remove(event.getPlayer().getUniqueId());}
    @Subscribe public void shutdown(ProxyShutdownEvent event){closed=true;requests.clear();if(commandMeta!=null)proxy.getCommandManager().unregister(commandMeta);proxy.getChannelRegistrar().unregister(CHANNEL);}
    private boolean turkish(CommandSource source){if(!(source instanceof Player player))return false;Locale locale=player.getEffectiveLocale();return locale!=null&&"tr".equals(locale.getLanguage());}
    private void localized(CommandSource source,String en,String tr){send(source,turkish(source)?tr:en);}
    private void send(CommandSource source,String text){source.sendMessage(Component.text("[DyClaim] ",NamedTextColor.LIGHT_PURPLE).append(Component.text(text,NamedTextColor.GRAY)));}
    private final class Diagnostics implements SimpleCommand {
        @Override public boolean hasPermission(Invocation invocation){return invocation.source().hasPermission(PERMISSION);}
        @Override public void execute(Invocation invocation){
            CommandSource source=invocation.source();if(!source.hasPermission(PERMISSION)){localized(source,"You do not have permission!","Bu komutu kullanma izniniz yok!");return;}
            String[] args=invocation.arguments();String word=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            if(args.length>1){usage(source);return;}
            if(Set.of("version","surum","sürüm").contains(word)){send(source,"DyClaim-v"+VERSION+" · Velocity bridge");return;}
            if(!Set.of("status","durum").contains(word)){usage(source);return;}
            if(!(source instanceof Player player)){localized(source,"Use status as a player connected to a backend.","Durum komutunu bir backend'e bağlı oyuncu olarak kullanın.");return;}
            var currentConnection=player.getCurrentServer();
            if(closed||currentConnection.isEmpty()){localized(player,"No connected backend.","Bağlı bir backend bulunamadı.");return;}
            ServerConnection connection=currentConnection.orElseThrow();String backend=connection.getServerInfo().getName();
            var ticket=requests.begin(player.getUniqueId(),backend);if(ticket.isEmpty()){localized(player,"A check is already pending or the queue is full.","Bir kontrol zaten bekliyor veya kuyruk dolu.");return;}
            if(!connection.sendPluginMessage(CHANNEL,StatusProtocol.request(ticket.get().id()))){requests.discard(player.getUniqueId(),ticket.get().id());localized(player,"Could not send the diagnostic request.","Kontrol isteği gönderilemedi.");return;}
            proxy.getScheduler().buildTask(VelocityBridge.this,()->{
                if(!closed&&requests.expire(player.getUniqueId(),ticket.get().id()))proxy.getPlayer(player.getUniqueId()).filter(p->p.hasPermission(PERMISSION)).ifPresent(p->localized(p,"No diagnostic reply. Check the backend bridge and dyclaim.admin permission.","Kontrol yanıtı gelmedi. Backend köprüsünü ve dyclaim.admin iznini kontrol edin."));
            }).delay(5,TimeUnit.SECONDS).schedule();
        }
        private void usage(CommandSource source){localized(source,"Usage: /dyclaimproxy <status|version>","Kullanım: /dyclaimproxy <durum|sürüm>");}
        @Override public List<String> suggest(Invocation invocation){
            if(!hasPermission(invocation)||invocation.arguments().length>1)return List.of();
            String prefix=invocation.arguments().length==0?"":invocation.arguments()[0].toLowerCase(Locale.ROOT);
            return (turkish(invocation.source())?List.of("durum","sürüm"):List.of("status","version")).stream().filter(word->word.startsWith(prefix)).toList();
        }
    }
}
