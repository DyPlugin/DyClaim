package dev.dyclaim.bungeecord;
import dev.dyclaim.platform.proxy.*;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.event.EventHandler;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class BungeeBridge extends Plugin implements Listener {
    public static final String PERMISSION="dyclaim.proxy.status";
    private final StatusRequests requests=new StatusRequests(System::nanoTime);
    private final Diagnostics diagnostics=new Diagnostics();
    private BungeeMessages messages;
    private volatile boolean closed;
    @Override public void onEnable(){
        getProxy().registerChannel(StatusProtocol.CHANNEL);
        messages=new BungeeMessages(requests,()->!closed,(player,reply)->send(player,"DyClaim-v"+reply.status().version()+" · "+reply.backend()+" · "+String.join(", ",new TreeSet<>(reply.status().capabilities()))));
        getProxy().getPluginManager().registerListener(this,messages);getProxy().getPluginManager().registerListener(this,this);
        boolean occupied=getProxy().getPluginManager().getCommands().stream().anyMatch(entry->entry.getKey().equalsIgnoreCase("dyclaimproxy"));
        if(occupied)getLogger().warning("dyclaimproxy is already registered; existing command preserved.");
        else getProxy().getPluginManager().registerCommand(this,diagnostics);
        getLogger().info("DyClaim v"+BridgeVersion.get()+" enabled! (BungeeCord bridge)");
    }
    @EventHandler public void switched(ServerSwitchEvent event){requests.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void disconnected(PlayerDisconnectEvent event){requests.remove(event.getPlayer().getUniqueId());}
    @Override public void onDisable(){closed=true;requests.clear();getProxy().getScheduler().cancel(this);getProxy().unregisterChannel(StatusProtocol.CHANNEL);getProxy().getPluginManager().unregisterCommand(diagnostics);if(messages!=null)getProxy().getPluginManager().unregisterListener(messages);getProxy().getPluginManager().unregisterListener(this);}
    private boolean turkish(CommandSender source){return source instanceof ProxiedPlayer player&&player.getLocale()!=null&&"tr".equals(player.getLocale().getLanguage());}
    private void localized(CommandSender source,String en,String tr){send(source,turkish(source)?tr:en);}
    private void send(CommandSender source,String text){source.sendMessage(TextComponent.fromLegacyText("§7[§dDyClaim§7] "+text));}
    private final class Diagnostics extends Command implements TabExecutor {
        Diagnostics(){super("dyclaimproxy",PERMISSION);}
        @Override public void execute(CommandSender source,String[] args){
            if(!source.hasPermission(PERMISSION)){localized(source,"You do not have permission!","Bu komutu kullanma izniniz yok!");return;}
            String word=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);if(args.length>1){usage(source);return;}
            if(Set.of("version","surum","sürüm").contains(word)){send(source,"DyClaim-v"+BridgeVersion.get()+" · BungeeCord bridge");return;}
            if(!Set.of("status","durum").contains(word)){usage(source);return;}
            if(!(source instanceof ProxiedPlayer player)){localized(source,"Use status as a player connected to a backend.","Durum komutunu bir backend'e bağlı oyuncu olarak kullanın.");return;}
            Server connection=player.getServer();if(closed||connection==null){localized(player,"No connected backend.","Bağlı bir backend bulunamadı.");return;}
            String backend=connection.getInfo().getName();var ticket=requests.begin(player.getUniqueId(),backend);
            if(ticket.isEmpty()){localized(player,"A check is already pending or the queue is full.","Bir kontrol zaten bekliyor veya kuyruk dolu.");return;}
            connection.sendData(StatusProtocol.CHANNEL,StatusProtocol.request(ticket.get().id()));
            getProxy().getScheduler().schedule(BungeeBridge.this,()->{
                if(!closed&&requests.expire(player.getUniqueId(),ticket.get().id())){ProxiedPlayer current=getProxy().getPlayer(player.getUniqueId());if(current!=null&&current.hasPermission(PERMISSION))localized(current,"No diagnostic reply. Check the backend bridge and dyclaim.admin permission.","Kontrol yanıtı gelmedi. Backend köprüsünü ve dyclaim.admin iznini kontrol edin.");}
            },5,TimeUnit.SECONDS);
        }
        private void usage(CommandSender source){localized(source,"Usage: /dyclaimproxy <status|version>","Kullanım: /dyclaimproxy <durum|sürüm>");}
        @Override public Iterable<String> onTabComplete(CommandSender source,String[] args){
            if(!source.hasPermission(PERMISSION)||args.length>1)return List.of();String prefix=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
            return (turkish(source)?List.of("durum","sürüm"):List.of("status","version")).stream().filter(word->word.startsWith(prefix)).toList();
        }
    }
}
