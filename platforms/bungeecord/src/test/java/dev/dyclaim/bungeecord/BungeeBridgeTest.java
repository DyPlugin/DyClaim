package dev.dyclaim.bungeecord;
import dev.dyclaim.platform.proxy.StatusProtocol;
import net.md_5.bungee.api.connection.*;
import net.md_5.bungee.api.event.PluginMessageEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.md_5.bungee.api.config.ServerInfo;
import java.util.*;

class BungeeBridgeTest {
    @Test void validReplyUsesCapturedBackendEvenIfPlayerDisconnectsBeforeRendering() throws Exception {
        BungeeBridge bridge=mock(BungeeBridge.class);var proxy=mock(net.md_5.bungee.api.ProxyServer.class);
        when(bridge.getProxy()).thenReturn(proxy);when(proxy.getPluginManager()).thenReturn(mock(net.md_5.bungee.api.plugin.PluginManager.class));
        when(bridge.getLogger()).thenReturn(java.util.logging.Logger.getLogger("bridge-test"));
        var requests=new dev.dyclaim.platform.proxy.StatusRequests(System::nanoTime);
        var field=BungeeBridge.class.getDeclaredField("requests");field.setAccessible(true);field.set(bridge,requests);
        doCallRealMethod().when(bridge).onEnable();bridge.onEnable();
        var messages=BungeeBridge.class.getDeclaredField("messages");messages.setAccessible(true);
        ProxiedPlayer player=mock(ProxiedPlayer.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.hasPermission("dyclaim.proxy.status")).thenReturn(true);
        Server source=mock(Server.class);ServerInfo info=mock(ServerInfo.class);when(source.getInfo()).thenReturn(info);when(info.getName()).thenReturn("survival");
        when(player.getServer()).thenReturn(source,(Server)null);
        var ticket=requests.begin(player.getUniqueId(),"survival").orElseThrow();
        var status=new StatusProtocol.Status(ticket.id(),"1.0.2",Set.of("claim"));
        var event=new PluginMessageEvent(source,player,StatusProtocol.CHANNEL,StatusProtocol.response(status));
        assertDoesNotThrow(()->((BungeeMessages)messages.get(bridge)).onMessage(event));
        verify(player,times(1)).getServer();
    }
    @Test void clientMessagesOnPrivateChannelAreCancelledAndNeverForwarded() {
        ProxiedPlayer client=mock(ProxiedPlayer.class);Server backend=mock(Server.class);
        var event=new PluginMessageEvent(client,backend,StatusProtocol.CHANNEL,new byte[]{1,2,3});
        listener().onMessage(event);
        assertTrue(event.isCancelled());verifyNoInteractions(backend);
    }
    @Test void unrelatedChannelsAreNotCancelled() {
        var event=new PluginMessageEvent(mock(ProxiedPlayer.class),mock(Server.class),"other:channel",new byte[0]);
        listener().onMessage(event);assertFalse(event.isCancelled());
    }
    private BungeeMessages listener(){return new BungeeMessages(new dev.dyclaim.platform.proxy.StatusRequests(System::nanoTime),()->true,(p,s)->{});}
    @Test void disconnectDuringResponseDoesNotThrowOrSendAReply() {
        ProxiedPlayer player=mock(ProxiedPlayer.class);when(player.hasPermission("dyclaim.proxy.status")).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Server source=mock(Server.class);ServerInfo info=mock(ServerInfo.class);when(info.getName()).thenReturn("survival");when(source.getInfo()).thenReturn(info);
        when(player.getServer()).thenReturn(source,(Server)null);
        var status=new dev.dyclaim.platform.proxy.StatusProtocol.Status(UUID.randomUUID(),"1.0.2",Set.of("claim"));
        var event=new PluginMessageEvent(source,player,StatusProtocol.CHANNEL,StatusProtocol.response(status));
        assertDoesNotThrow(()->listener().onMessage(event));
    }
}
