package dev.dyclaim.velocity;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import com.velocitypowered.api.command.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.dyclaim.platform.proxy.*;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.net.InetSocketAddress;
import java.util.*;

class VelocityBridgeTest {
    @Test void aBackendChangeBetweenConnectionReadsDoesNotBreakTheStatusCommand() {
        ProxyServer proxy=mock(ProxyServer.class);when(proxy.getScheduler()).thenReturn(mock(com.velocitypowered.api.scheduler.Scheduler.class,RETURNS_DEEP_STUBS));
        Player player=mock(Player.class);when(player.hasPermission(VelocityBridge.PERMISSION)).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ServerConnection backend=mock(ServerConnection.class);when(backend.getServerInfo()).thenReturn(new ServerInfo("survival",new InetSocketAddress("127.0.0.1",25566)));
        when(player.getCurrentServer()).thenReturn(Optional.of(backend),Optional.empty());
        SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(player);when(invocation.arguments()).thenReturn(new String[]{"status"});
        assertDoesNotThrow(()->new VelocityBridge(proxy,mock(Logger.class)).command().execute(invocation));
        verify(player).getCurrentServer();
    }
    @Test void diagnosticReplyMustMatchRequestedBackendAndCanBeConsumedOnlyOnce() throws Exception {
        ProxyServer proxy=mock(ProxyServer.class);when(proxy.getScheduler()).thenReturn(mock(com.velocitypowered.api.scheduler.Scheduler.class,RETURNS_DEEP_STUBS));
        Player player=mock(Player.class);when(player.hasPermission(VelocityBridge.PERMISSION)).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ServerConnection backend=mock(ServerConnection.class),other=mock(ServerConnection.class);
        when(backend.getServerInfo()).thenReturn(new ServerInfo("survival",new InetSocketAddress("127.0.0.1",25566)));
        when(other.getServerInfo()).thenReturn(new ServerInfo("creative",new InetSocketAddress("127.0.0.1",25567)));
        when(player.getCurrentServer()).thenReturn(Optional.of(backend));when(backend.sendPluginMessage(eq(VelocityBridge.CHANNEL),any(byte[].class))).thenReturn(true);
        SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(player);when(invocation.arguments()).thenReturn(new String[]{"status"});
        VelocityBridge bridge=new VelocityBridge(proxy,mock(Logger.class));bridge.command().execute(invocation);
        var payload=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(backend).sendPluginMessage(eq(VelocityBridge.CHANNEL),payload.capture());
        var reply=StatusProtocol.response(new StatusProtocol.Status(StatusProtocol.decodeRequest(payload.getValue()),"1.0.2",Set.of("trust","claim")));
        bridge.onMessage(new PluginMessageEvent(other,player,VelocityBridge.CHANNEL,reply));verify(player,never()).sendMessage(any(Component.class));
        bridge.onMessage(new PluginMessageEvent(backend,player,VelocityBridge.CHANNEL,reply));
        bridge.onMessage(new PluginMessageEvent(backend,player,VelocityBridge.CHANNEL,reply));
        var text=org.mockito.ArgumentCaptor.forClass(Component.class);verify(player).sendMessage(text.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(text.getValue()).contains("survival · claim, trust"));
    }
    @Test void clientMessagesOnPrivateChannelAreHandledAndNeverForwarded() {
        ProxyServer proxy=mock(ProxyServer.class);Player client=mock(Player.class);ServerConnection backend=mock(ServerConnection.class);
        var event=new PluginMessageEvent(client,backend,VelocityBridge.CHANNEL,new byte[]{1,2,3});
        new VelocityBridge(proxy,mock(Logger.class)).onMessage(event);
        assertFalse(event.getResult().isAllowed());verifyNoInteractions(backend);
    }
    @Test void unrelatedPluginChannelsAreUntouched() {
        var event=new PluginMessageEvent(mock(Player.class),mock(ServerConnection.class),MinecraftChannelIdentifier.from("other:channel"),new byte[0]);
        new VelocityBridge(mock(ProxyServer.class),mock(Logger.class)).onMessage(event);
        assertTrue(event.getResult().isAllowed());
    }
    @Test void deniedDiagnosticsSendOneErrorAndNeverInspectTheBackend() {
        Player player=mock(Player.class);SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(player);
        new VelocityBridge(mock(ProxyServer.class),mock(Logger.class)).command().execute(invocation);
        verify(player).sendMessage(any(Component.class));verify(player,never()).getCurrentServer();
    }
    @Test void versionCommandShowsOneVersionPrefixFromConsole() {
        CommandSource console=mock(CommandSource.class);when(console.hasPermission("dyclaim.proxy.status")).thenReturn(true);
        SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(console);when(invocation.arguments()).thenReturn(new String[]{"version"});
        new VelocityBridge(mock(ProxyServer.class),mock(Logger.class)).command().execute(invocation);
        var text=org.mockito.ArgumentCaptor.forClass(Component.class);verify(console).sendMessage(text.capture());
        String plain=PlainTextComponentSerializer.plainText().serialize(text.getValue());assertTrue(plain.contains("DyClaim-v1.0.2"));assertFalse(plain.contains("vv1.0.2"));
    }
    @Test void disconnectDuringResponseDoesNotThrowOrSendAReply() {
        Player player=mock(Player.class);when(player.hasPermission("dyclaim.proxy.status")).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ServerConnection connection=mock(ServerConnection.class);when(connection.getServerInfo()).thenReturn(new ServerInfo("survival",new InetSocketAddress("127.0.0.1",25566)));
        when(player.getCurrentServer()).thenReturn(Optional.of(connection),Optional.empty());
        var status=new StatusProtocol.Status(UUID.randomUUID(),"1.0.2",Set.of("claim"));
        var event=new PluginMessageEvent(connection,player,VelocityBridge.CHANNEL,StatusProtocol.response(status));
        assertDoesNotThrow(()->new VelocityBridge(mock(ProxyServer.class),mock(Logger.class)).onMessage(event));
        verify(player,never()).sendMessage(any(Component.class));
    }
}
