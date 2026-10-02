package dev.dyclaim;

import dev.dyclaim.manager.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

abstract class ManagerFixture {
    @TempDir Path dir;
    DyClaim plugin;
    YamlConfiguration values;
    ConfigManager config;
    ClaimManager claims;
    EconomyManager economy;
    FeatureManager features;
    ConfirmationManager confirmations;
    TransactionManager transactions;
    AccessManager access;
    BukkitScheduler scheduler;
    World world;
    MockedStatic<Bukkit> bukkit;
    List<Runnable> timers;
    @BeforeEach void setup() throws Exception {
        bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);
        plugin=mock(DyClaim.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        PluginManager pluginManager=server.getPluginManager();bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        scheduler=mock(BukkitScheduler.class);when(server.getScheduler()).thenReturn(scheduler);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);timers=new ArrayList<>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(invocation->{timers.add(invocation.getArgument(1));return mock(BukkitTask.class);});
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
        values=new YamlConfiguration();values.loadFromString(new String(getClass().getResourceAsStream("/config.yml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        config=mock(ConfigManager.class);when(plugin.getConfigManager()).thenReturn(config);when(config.values()).thenReturn(values);
        when(config.getClaimPrice()).thenReturn(1000.0);when(config.getMaxClaimsPerPlayer()).thenAnswer(i->values.getInt("claim.max-claims-per-player"));when(config.isAllowClaiming()).thenAnswer(i->values.getBoolean("claim.allow-claiming"));
        when(config.isWorldBlacklisted(anyString())).thenAnswer(i->values.getStringList("blacklisted-worlds").contains(i.getArgument(0)));when(config.getSellRefundPercent()).thenReturn(60);
        MessageManager messages=mock(MessageManager.class);when(plugin.getMessageManager()).thenReturn(messages);when(messages.getPrefixed(any(org.bukkit.command.CommandSender.class),anyString())).thenAnswer(i->i.getArgument(1));when(messages.getPrefixed(any(org.bukkit.command.CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        when(messages.getMessage(any(org.bukkit.command.CommandSender.class),anyString())).thenAnswer(i->i.getArgument(1));
        economy=mock(EconomyManager.class);when(plugin.getEconomyManager()).thenReturn(economy);when(economy.isEnabled()).thenReturn(true);when(economy.isAvailable()).thenReturn(true);when(economy.hasEnough(any(OfflinePlayer.class),anyDouble())).thenReturn(true);when(economy.withdraw(any(UUID.class),anyDouble())).thenReturn(true);when(economy.deposit(any(UUID.class),anyDouble())).thenReturn(true);when(economy.formatMoney(anyDouble())).thenAnswer(i->String.valueOf((double)i.getArgument(0)));
        access=new AccessManager(plugin);when(plugin.getAccessManager()).thenReturn(access);
        var rules=new AcquisitionRules(plugin);when(plugin.getAcquisitionRules()).thenReturn(rules);
        claims=new ClaimManager(plugin);when(plugin.getClaimManager()).thenReturn(claims);
        confirmations=new ConfirmationManager(plugin);when(plugin.getConfirmationManager()).thenReturn(confirmations);
        transactions=new TransactionManager(plugin);when(plugin.getTransactionManager()).thenReturn(transactions);
        ClanTrustManager clans=mock(ClanTrustManager.class);when(plugin.getClanTrustManager()).thenReturn(clans);
        when(plugin.getCooldownManager()).thenReturn(mock(CooldownManager.class));when(plugin.getChunkVisualizer()).thenReturn(mock(dev.dyclaim.visualizer.ChunkVisualizer.class));
        world=mock(World.class);when(world.getName()).thenReturn("world");bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
        features=new FeatureManager(plugin);when(plugin.getFeatureManager()).thenReturn(features);
    }
    Chunk chunk(int x,int z){Chunk c=mock(Chunk.class);when(c.getWorld()).thenReturn(world);when(c.getX()).thenReturn(x);when(c.getZ()).thenReturn(z);when(world.getChunkAt(x,z)).thenReturn(c);return c;}
    Player player(String name,Chunk c){
        Player p=mock(Player.class);UUID uuid=UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(p.getUniqueId()).thenReturn(uuid);when(p.getName()).thenReturn(name);when(p.isOnline()).thenReturn(true);when(p.getWorld()).thenReturn(world);
        when(p.hasPermission(anyString())).thenAnswer(i->!Set.of("dyclaim.admin","dyclaim.admin.bypass","dyclaim.admin.purge.exempt","dyclaim.warn.exempt").contains(i.getArgument(0)));
        Location l=mock(Location.class);when(l.getChunk()).thenReturn(c);when(l.getWorld()).thenReturn(world);when(p.getLocation()).thenReturn(l);
        bukkit.when(()->Bukkit.getPlayer(uuid)).thenReturn(p);bukkit.when(()->Bukkit.getPlayerExact(name)).thenReturn(p);when(plugin.getServer().getPlayer(uuid)).thenReturn(p);
        when(p.spigot()).thenReturn(mock(Player.Spigot.class));return p;
    }
    @AfterEach void teardown(){bukkit.close();}
}
