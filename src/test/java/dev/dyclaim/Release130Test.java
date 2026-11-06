package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import dev.dyclaim.listener.ClaimProtectionListener;
import dev.dyclaim.listener.PlayerListener;
import dev.dyclaim.manager.*;
import dev.dyclaim.model.ClaimData;
import dev.dyclaim.platform.core.ProtectionRules;
import dev.dyclaim.util.AtomicJson;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release130Test extends ManagerFixture {
    private Location at(Chunk chunk,int blockX,int blockZ){
        Location l=mock(Location.class);when(l.getChunk()).thenReturn(chunk);when(l.getWorld()).thenReturn(world);when(l.getBlockX()).thenReturn(blockX);when(l.getBlockZ()).thenReturn(blockZ);return l;
    }
    private PlayerMoveEvent step(Player p,Location from,Location to){return new PlayerMoveEvent(p,from,to);}
    private Block block(Chunk chunk,Material type){Block b=mock(Block.class);when(b.getChunk()).thenReturn(chunk);when(b.getType()).thenReturn(type);return b;}
    /** Flight state on a mocked player behaves like the real one. */
    private AtomicBoolean[] flightState(Player p){
        AtomicBoolean allow=new AtomicBoolean(),flying=new AtomicBoolean();
        when(p.getAllowFlight()).thenAnswer(i->allow.get());doAnswer(i->{allow.set(i.getArgument(0));if(!allow.get())flying.set(false);return null;}).when(p).setAllowFlight(anyBoolean());
        when(p.isFlying()).thenAnswer(i->flying.get());doAnswer(i->{flying.set(i.getArgument(0));return null;}).when(p).setFlying(anyBoolean());
        when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);return new AtomicBoolean[]{allow,flying};
    }

    // Hot settings resolve exactly as the original rules.
    @Test void cachedPolicyResolutionMatchesTheOriginalRules(){
        String[] keys={"pvp","explosions","mob-explosions","mob-spawning","villager-player-damage","visitor-doors","visitor-trapdoors","visitor-containers","fire-spread","visitor-entry"};
        Object[] choices={null,true,false};
        for(int variant=0;variant<6;variant++){
            YamlConfiguration config=new YamlConfiguration();
            if(variant==1)config.set("protection.explosion-disabled",false);
            if(variant==2){config.set("protection.fire-spread.forced-value",true);config.set("protection.visitor-entry.player-toggle",false);}
            if(variant==3){config.set("claim-rules.worlds.world.protection.pvp.default",true);config.set("claim-rules.worlds.world.protection.visitor-entry.forced-value",false);}
            if(variant==4){config.set("protection.mob-explosions.default",true);config.set("claim-rules.worlds.world.protection.visitor-doors.player-toggle",false);}
            if(variant==5){config.set("protection.pvp-disabled",false);config.set("protection.visitor-entry.default",false);}
            HotSettings hot=HotSettings.of(config);
            for(String key:keys)for(Object choice:choices)for(Object explosion:choices){
                ClaimData c=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);c.inheritChoices();
                if(choice!=null)c.setChoice(key,(Boolean)choice);
                if(explosion!=null&&!key.equals("explosions"))c.setChoice("explosions",(Boolean)explosion);
                assertEquals(ProtectionPolicy.resolve(config,c,key),hot.resolve(c,key),variant+":"+key+":"+choice+":"+explosion);
            }
        }
    }
    @Test void newProtectionSettingsKeepUpgradingServersUnchanged(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk);claims.claimChunk(owner,chunk);ClaimData c=claims.getClaimAt(chunk);
        assertFalse(access.enabled(c,"fire-spread"));assertTrue(access.enabled(c,"visitor-entry"));
        YamlConfiguration empty=new YamlConfiguration();
        assertFalse(ProtectionPolicy.resolve(empty,c,"fire-spread").enabled());assertTrue(ProtectionPolicy.resolve(empty,c,"visitor-entry").enabled(),"a config without the key keeps claims open");
        assertTrue(ProtectionRules.fallback(new ProtectionRules.Config(){public Object get(String p){return null;}public boolean contains(String p){return false;}},"visitor-entry"));
    }

    // Fire spread
    @Test void fireStaysOutOfClaimsUntilTheOwnerAllowsIt(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk);claims.claimChunk(owner,chunk);var listener=new ClaimProtectionListener(plugin);
        Block wood=block(chunk,Material.OAK_PLANKS),fire=block(chunk,Material.FIRE);
        var burn=mock(BlockBurnEvent.class);when(burn.getBlock()).thenReturn(wood);listener.burn(burn);verify(burn).setCancelled(true);
        var spread=mock(BlockSpreadEvent.class);when(spread.getBlock()).thenReturn(wood);when(spread.getSource()).thenReturn(fire);listener.onBlockSpread(spread);verify(spread).setCancelled(true);
        var ignite=mock(BlockIgniteEvent.class);when(ignite.getBlock()).thenReturn(wood);when(ignite.getCause()).thenReturn(BlockIgniteEvent.IgniteCause.LAVA);listener.onBlockIgnite(ignite);verify(ignite).setCancelled(true);
        features.handle(owner,new String[]{"fire"});assertTrue(access.enabled(claims.getClaimAt(chunk),"fire-spread"));
        var burn2=mock(BlockBurnEvent.class);when(burn2.getBlock()).thenReturn(wood);listener.burn(burn2);verify(burn2,never()).setCancelled(true);
        var spread2=mock(BlockSpreadEvent.class);when(spread2.getBlock()).thenReturn(wood);when(spread2.getSource()).thenReturn(fire);listener.onBlockSpread(spread2);verify(spread2,never()).setCancelled(true);
        var ignite2=mock(BlockIgniteEvent.class);when(ignite2.getBlock()).thenReturn(wood);when(ignite2.getCause()).thenReturn(BlockIgniteEvent.IgniteCause.SPREAD);listener.onBlockIgnite(ignite2);verify(ignite2,never()).setCancelled(true);
        features.handle(owner,new String[]{"ateş"});assertFalse(access.enabled(claims.getClaimAt(chunk),"fire-spread"),"the Turkish word toggles it back");
    }
    @Test void playersStillNeedBuildRightsToLightFiresAndServersCanLockFire(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);claims.getClaimAt(chunk).setChoice("fire-spread",true);
        Block grass=block(chunk,Material.GRASS_BLOCK);var ignite=mock(BlockIgniteEvent.class);when(ignite.getBlock()).thenReturn(grass);when(ignite.getCause()).thenReturn(BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL);when(ignite.getPlayer()).thenReturn(visitor);
        new ClaimProtectionListener(plugin).onBlockIgnite(ignite);verify(ignite).setCancelled(true);
        values.set("protection.fire-spread.forced-value",false);assertFalse(access.enabled(claims.getClaimAt(chunk),"fire-spread"));
        features.handle(owner,new String[]{"fire"});verify(owner).sendMessage("setting-locked");
    }

    // Closed claims
    @Test void closedClaimsKeepOutVisitorsButNotTrustedPlayers(){
        var inside=chunk(0,0);var outside=chunk(1,0);Player owner=player("Owner",inside),visitor=player("Visitor",outside),friend=player("Friend",outside);
        claims.claimChunk(owner,inside);for(Player p:List.of(visitor,friend))when(p.hasPermission("dyclaim.ban.exempt")).thenReturn(false);
        var open=step(visitor,at(outside,24,8),at(inside,8,8));features.entry(open);assertFalse(open.isCancelled(),"claims are open by default");
        features.handle(owner,new String[]{"entry"});assertFalse(access.enabled(claims.getClaimAt(inside),"visitor-entry"));
        var closed=step(visitor,at(outside,24,8),at(inside,8,8));features.entry(closed);assertTrue(closed.isCancelled());
        verify(visitor).sendMessage("visitor-entry-closed");
        features.handle(owner,new String[]{"trust","Friend"});
        var trusted=step(friend,at(outside,24,8),at(inside,8,8));features.entry(trusted);assertFalse(trusted.isCancelled());
        var back=step(owner,at(outside,24,8),at(inside,8,8));features.entry(back);assertFalse(back.isCancelled());
        when(visitor.hasPermission("dyclaim.ban.exempt")).thenReturn(true);
        var exempt=step(visitor,at(outside,24,8),at(inside,8,8));features.entry(exempt);assertFalse(exempt.isCancelled());
        features.handle(owner,new String[]{"giriş"});assertTrue(access.enabled(claims.getClaimAt(inside),"visitor-entry"));
    }
    @Test void serverCanForceClaimsOpen(){
        var inside=chunk(0,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);
        values.set("protection.visitor-entry.forced-value",true);features.handle(owner,new String[]{"entry"});
        verify(owner).sendMessage("setting-locked");assertTrue(access.enabled(claims.getClaimAt(inside),"visitor-entry"));
    }

    // Claim flight
    @Test void ownersFlyInTheirClaimAndLoseFlightOutside(){
        Chunk inside=chunk(0,0),outside=chunk(1,0);Player owner=player("Owner",inside);var state=flightState(owner);claims.claimChunk(owner,inside);
        features.handle(owner,new String[]{"fly"});verify(owner).sendMessage("fly-enabled");assertTrue(state[0].get());
        owner.setFlying(true);
        features.move(step(owner,at(inside,8,8),at(outside,24,8)));
        assertFalse(state[0].get());assertFalse(state[1].get());verify(owner).sendMessage("fly-left");
        var fall=mock(EntityDamageEvent.class);when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);when(fall.getEntity()).thenReturn(owner);
        features.fall(fall);verify(fall).setCancelled(true);
        var second=mock(EntityDamageEvent.class);when(second.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);when(second.getEntity()).thenReturn(owner);
        features.fall(second);verify(second,never()).setCancelled(true);
        features.move(step(owner,at(outside,24,8),at(inside,8,8)));assertTrue(state[0].get(),"flight resumes in the claim");
        features.handle(owner,new String[]{"uç","kapat"});assertFalse(state[0].get());verify(owner).sendMessage("fly-disabled");
    }
    @Test void flightNeedsTheClaimPermissionAndTheFeature(){
        var inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside),friend=player("Friend",inside);
        var visitorState=flightState(visitor);var friendState=flightState(friend);flightState(owner);claims.claimChunk(owner,inside);
        features.handle(visitor,new String[]{"fly"});verify(visitor).sendMessage("fly-not-here");assertFalse(visitorState[0].get());
        features.handle(owner,new String[]{"trust","Friend"});features.handle(friend,new String[]{"fly"});assertTrue(friendState[0].get());
        features.handle(friend,new String[]{"fly","off"});values.set("claim-fly.allow-trusted",false);
        features.handle(friend,new String[]{"fly"});verify(friend).sendMessage("fly-not-here");
        when(owner.hasPermission("dyclaim.fly")).thenReturn(false);features.handle(owner,new String[]{"fly"});verify(owner).sendMessage("no-permission");
        values.set("claim-fly.enabled",false);features.handle(friend,new String[]{"fly"});verify(friend).sendMessage("feature-disabled");
    }
    @Test void flightFromOtherSourcesIsNeverTakenAway(){
        Chunk inside=chunk(0,0),outside=chunk(1,0);Player owner=player("Owner",inside);var state=flightState(owner);claims.claimChunk(owner,inside);
        state[0].set(true);features.handle(owner,new String[]{"fly"});
        features.move(step(owner,at(inside,8,8),at(outside,24,8)));
        assertTrue(state[0].get(),"flight granted by creative mode or another plugin stays");verify(owner,never()).sendMessage("fly-left");
    }
    @Test void reloadEndsFlightWhenTheFeatureIsTurnedOff(){
        var inside=chunk(0,0);Player owner=player("Owner",inside);var state=flightState(owner);claims.claimChunk(owner,inside);
        features.handle(owner,new String[]{"fly"});assertTrue(state[0].get());
        values.set("claim-fly.enabled",false);features.reloadTasks();assertFalse(state[0].get());
        values.set("claim-fly.enabled",true);features.move(step(owner,at(chunk(1,0),24,8),at(inside,8,8)));assertFalse(state[0].get(),"turning the feature off also clears the choice");
    }

    // Notices
    @Test void playersCanHideEntryAndExitNotices() throws Exception {
        var inside=chunk(1,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);Player visitor=player("Visitor",chunk(0,0));
        when(config.isShowEnter()).thenReturn(true);when(config.isShowLeave()).thenReturn(true);var listener=new PlayerListener(plugin);
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));verify(visitor).sendMessage("protection-enter");
        features.handle(visitor,new String[]{"notices"});verify(visitor).sendMessage("notices-disabled");clearInvocations(visitor);
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));
        listener.onPlayerMove(step(visitor,new Location(world,24,64,8),new Location(world,8,64,8)));
        verify(visitor,never()).sendMessage("protection-enter");verify(visitor,never()).sendMessage("protection-leave");
        FeatureManager.State saved=AtomicJson.read(dir.resolve("state.json"),FeatureManager.State.class);assertTrue(saved.quietNotices.contains(visitor.getUniqueId()));
        values.set("notification.player-toggle",false);
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));verify(visitor).sendMessage("protection-enter");
        features.handle(visitor,new String[]{"bildirim","aç"});verify(visitor).sendMessage("feature-disabled");
        values.set("notification.player-toggle",true);features.handle(visitor,new String[]{"bildirim","aç"});verify(visitor).sendMessage("notices-enabled");
    }
    @Test void stateFilesFromOlderVersionsLoadWithNoticesShown() throws Exception {
        java.nio.file.Files.writeString(dir.resolve("state.json"),"{\"version\":1,\"lastSeen\":{},\"bans\":{},\"warnings\":{},\"languages\":{}}");
        var loaded=new FeatureManager(plugin);assertFalse(loaded.noticesHidden(UUID.randomUUID()));
    }
    @Test void namedClaimsShowTheirNameAndCrossingBetweenNamesNotifies(){
        Player owner=player("Owner",chunk(1,0));claims.claimChunk(owner,chunk(1,0));claims.claimChunk(owner,chunk(2,0));
        claims.getClaimAt("world",1,0).setName("Farm");claims.getClaimAt("world",2,0).setName("Castle");
        Player visitor=player("Visitor",chunk(0,0));when(config.isShowEnter()).thenReturn(true);var listener=new PlayerListener(plugin);
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));verify(visitor).sendMessage("protection-enter-named");
        clearInvocations(visitor);listener.onPlayerMove(step(visitor,new Location(world,24,64,8),new Location(world,40,64,8)));verify(visitor).sendMessage("protection-enter-named");
        values.set("notification.show-claim-name",false);clearInvocations(visitor);
        listener.onPlayerMove(step(visitor,new Location(world,40,64,8),new Location(world,24,64,8)));verify(visitor,never()).sendMessage(anyString());
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));verify(visitor).sendMessage("protection-enter");
    }
    @Test void noticesCanBeShownAsTitles(){
        Player owner=player("Owner",chunk(1,0));claims.claimChunk(owner,chunk(1,0));Player visitor=player("Visitor",chunk(0,0));
        when(config.isShowEnter()).thenReturn(true);when(config.isShowLeave()).thenReturn(true);values.set("notification.use-title",true);
        when(plugin.getMessageManager().getMessage(any(org.bukkit.command.CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        var listener=new PlayerListener(plugin);
        listener.onPlayerMove(step(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));verify(visitor).sendTitle("title-enter","title-enter-subtitle",5,30,10);
        listener.onPlayerMove(step(visitor,new Location(world,24,64,8),new Location(world,8,64,8)));verify(visitor).sendTitle("","actionbar-leave",5,30,10);
    }

    // Commands and help
    @Test void newCommandsAppearInHelpAndTabInBothLanguages(){
        Player p=player("Owner",chunk(0,0));var command=new ClaimCommand(plugin);List<String> rows=new ArrayList<>();
        when(plugin.getMessageManager().getMessage(eq(p),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));when(plugin.getMessageManager().getPrefix()).thenReturn("");
        for(int page=1;page<=6;page++){clearInvocations(p);command.onCommand(p,null,"claim",new String[]{"help",String.valueOf(page)});
            var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(p,atLeast(0)).sendMessage(captor.capture());rows.addAll(captor.getAllValues());}
        for(String row:List.of("help-fire","help-entry","help-fly","help-notices"))assertEquals(1,Collections.frequency(rows,row),row);
        var roots=command.onTabComplete(p,null,"claim",new String[]{""});assertTrue(roots.containsAll(List.of("fire","entry","fly","notices")));
        when(config.getLang()).thenReturn("tr");var messages=new MessageManager(plugin);when(plugin.getMessageManager()).thenReturn(messages);
        assertTrue(command.onTabComplete(p,null,"claim",new String[]{""}).containsAll(List.of("ateş","giriş","uç","bildirim")));
        assertEquals(List.of("aç","kapat"),command.onTabComplete(p,null,"claim",new String[]{"uç",""}));
        when(p.hasPermission("dyclaim.fly")).thenReturn(false);values.set("notification.player-toggle",false);
        assertFalse(command.onTabComplete(p,null,"claim",new String[]{""}).contains("uç"));assertFalse(command.onTabComplete(p,null,"claim",new String[]{""}).contains("bildirim"));
    }
    @Test void flyPermissionIsOptInAndEveryLanguageExplainsTheNewCommands() throws Exception {
        var plugin=new YamlConfiguration();plugin.loadFromString(new String(getClass().getResourceAsStream("/plugin.yml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("op",plugin.getString("permissions.dyclaim.fly.default"));assertFalse(plugin.getBoolean("permissions.dyclaim.player.children.dyclaim.fly"));
        for(String lang:MessageManager.LANGUAGES){
            var messages=new YamlConfiguration();messages.loadFromString(new String(getClass().getResourceAsStream("/messages_"+lang+".yml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            for(String key:List.of("help-fire","help-entry","help-fly","help-notices","setting-fire-spread","setting-visitor-entry","visitor-entry-closed","fly-left","notices-disabled","title-enter-named"))assertNotNull(messages.getString(key),lang+":"+key);
        }
    }

    // Optimizations
    @Test void chunkIndexFollowsClaimsThroughCreationRemovalAndReload(){
        Player owner=player("Owner",chunk(0,0));claims.claimChunk(owner,chunk(-3,7));
        assertNotNull(claims.getClaimAt("world",-3,7));assertNull(claims.getClaimAt("world",7,-3));assertNull(claims.getClaimAt("nether",-3,7));assertEquals(1,claims.getClaimCount());
        claims.saveAllSync();var reloaded=new ClaimManager(plugin);assertEquals(reloaded.getByKey("world:-3:7"),reloaded.getClaimAt("world",-3,7));
        assertTrue(claims.unclaimChunk("world",-3,7));assertNull(claims.getClaimAt("world",-3,7));assertFalse(claims.isChunkClaimed("world",-3,7));assertEquals(0,claims.getClaimCount());
        assertNotNull(claims.serverClaim("world",Integer.MAX_VALUE,Integer.MIN_VALUE));assertTrue(claims.isChunkClaimed("world",Integer.MAX_VALUE,Integer.MIN_VALUE));
    }
    @Test void spacingFindsNeighboursByLookupWhenManyClaimsExist(){
        values.set("claim-rules.spacing.enabled",true);values.set("claim-rules.spacing.minimum-empty-chunks",1);values.set("claim.max-claims-per-player",100);
        Player far=player("Far",chunk(0,0));for(int i=0;i<12;i++)claims.claimChunk(far,chunk(100+i*3,100));
        Player owner=player("Owner",chunk(0,0)),buyer=player("Buyer",chunk(0,0));claims.claimChunk(owner,chunk(0,0));
        var rules=new AcquisitionRules(plugin);
        assertEquals("acquire-spacing",rules.check(buyer,chunk(1,1),null,false));
        assertEquals("acquire-spacing",rules.check(buyer,chunk(-1,0),null,false));
        assertNull(rules.check(buyer,chunk(2,0),null,false));assertNull(rules.check(owner,chunk(1,0),null,false),"the same owner is exempt");
    }
    @Test void realConfigurationPublishesHotSettingsWithEveryChange() throws Exception {
        values.set("blacklisted-worlds",List.of("void"));values.save(dir.resolve("config.yml").toFile());when(plugin.getConfig()).thenReturn(values);
        ConfigManager manager=new ConfigManager(plugin);var first=manager.hot();assertNotNull(first);
        assertTrue(manager.isWorldBlacklisted("void"));assertFalse(manager.isWorldBlacklisted("world"));
        manager.setClaimPrice(700);assertNotSame(first,manager.hot());
        assertTrue(manager.removeBlacklistedWorld("void"));assertFalse(manager.isWorldBlacklisted("void"));
        when(plugin.getConfigManager()).thenReturn(manager);ClaimData c=new ClaimData(UUID.randomUUID(),"Owner","world",0,0);c.inheritChoices();
        assertEquals(ProtectionPolicy.resolve(manager.values(),c,"pvp"),access.policy(c,"pvp"));
    }
    @Test void joinsAndQuitsWriteStateOnceInTheBackground() throws Exception {
        when(scheduler.runTaskAsynchronously(eq(plugin),any(Runnable.class))).thenAnswer(i->{((Runnable)i.getArgument(1)).run();return mock(org.bukkit.scheduler.BukkitTask.class);});
        Player a=player("A",chunk(0,0)),b=player("B",chunk(0,0));timers.clear();
        features.join(new PlayerJoinEvent(a,""));features.join(new PlayerJoinEvent(b,""));
        assertFalse(java.nio.file.Files.exists(dir.resolve("state.json")),"nothing is written on the server thread");
        assertEquals(1,timers.size(),"both joins share one write");timers.get(0).run();
        FeatureManager.State saved=AtomicJson.read(dir.resolve("state.json"),FeatureManager.State.class);
        assertTrue(saved.lastSeen.containsKey(a.getUniqueId()));assertTrue(saved.lastSeen.containsKey(b.getUniqueId()));
    }
    @Test void messagePrefixIsColouredOnceUntilItChanges(){
        when(config.getPrefix()).thenReturn("&7[Claims]");var messages=new MessageManager(plugin);
        String first=messages.getPrefix();assertSame(first,messages.getPrefix());assertEquals("§7[Claims] ",first);
        when(config.getPrefix()).thenReturn("&a[New]");assertEquals("§a[New] ",messages.getPrefix());
    }
}
