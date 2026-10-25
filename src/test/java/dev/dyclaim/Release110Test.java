package dev.dyclaim;

import dev.dyclaim.api.DyClaimAPI;
import dev.dyclaim.api.event.*;
import dev.dyclaim.hook.ClaimChunkImport;
import dev.dyclaim.hook.map.ClaimAreas;
import dev.dyclaim.manager.ClaimImporter;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release110Test extends ManagerFixture {
    @BeforeEach void messages(){
        when(plugin.getMessageManager().getMessage(any(CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        when(plugin.getMessageManager().getPreferredLanguage(any())).thenReturn("en");
        when(world.getChunkAt(any(Location.class))).thenAnswer(i->{Location l=i.getArgument(0);return chunkAt(l.getBlockX()>>4,l.getBlockZ()>>4);});
    }
    @AfterEach void listeners(){HandlerList.unregisterAll();}
    private final Map<Long,Chunk> chunks=new HashMap<>();
    Chunk chunkAt(int x,int z){return chunks.computeIfAbsent(((long)x<<32)|(z&0xffffffffL),k->chunk(x,z));}
    Player visitorPlayer(String name,Chunk c){Player p=player(name,c);when(p.hasPermission("dyclaim.ban.exempt")).thenReturn(false);return p;}
    void grant(Player p,String... nodes){
        Set<PermissionAttachmentInfo> infos=new HashSet<>();
        for(String node:nodes){PermissionAttachmentInfo info=mock(PermissionAttachmentInfo.class);when(info.getPermission()).thenReturn(node);when(info.getValue()).thenReturn(true);infos.add(info);}
        when(p.getEffectivePermissions()).thenReturn(infos);
    }

    // Permission-based limits
    @Test void highestLimitPermissionReplacesTheConfiguredTotal(){
        var p=player("Owner",chunkAt(0,0));
        assertEquals(10,plugin.getAcquisitionRules().limit(p));
        grant(p,"dyclaim.limit.3","dyclaim.limit.25","dyclaim.limit.notanumber");
        assertEquals(25,plugin.getAcquisitionRules().limit(p));
        grant(p,"dyclaim.limit.2");
        assertEquals(2,plugin.getAcquisitionRules().limit(p),"a lower permission can also restrict a group");
        grant(p,"dyclaim.limit.unlimited");
        assertEquals(Integer.MAX_VALUE,plugin.getAcquisitionRules().limit(p));
        values.set("claim.permission-limits.enabled",false);
        assertEquals(10,plugin.getAcquisitionRules().limit(p));
    }
    @Test void permissionLimitIsEnforcedAndAlsoAppliesToWorldsWithoutOverride(){
        var p=player("Owner",chunkAt(0,0));grant(p,"dyclaim.limit.2");
        assertTrue(claims.claimChunk(p,chunkAt(0,0)));assertTrue(claims.claimChunk(p,chunkAt(5,5)));
        assertEquals("acquire-limit",plugin.getAcquisitionRules().check(p,chunkAt(9,9),null,false));
        grant(p,"dyclaim.limit.20");
        for(int i=0;i<12;i++)assertTrue(claims.claimChunk(p,chunkAt(20+i*3,0)));
        assertNull(plugin.getAcquisitionRules().check(p,chunkAt(90,90),null,false),"world limit falls back to the effective total, not to the config value");
    }

    // API and events
    @Test void apiReturnsDetachedSnapshotsAndRefusesWhenDisabled() throws Exception {
        var field=DyClaim.class.getDeclaredField("instance");field.setAccessible(true);Object old=field.get(null);
        try{
            field.set(null,null);
            assertFalse(DyClaimAPI.isAvailable());
            assertThrows(IllegalStateException.class,()->DyClaimAPI.getClaims(UUID.randomUUID()));
            field.set(null,plugin);when(plugin.isEnabled()).thenReturn(true);
            var p=player("Owner",chunkAt(1,2));claims.claimChunk(p,chunkAt(1,2));
            var claim=DyClaimAPI.getClaimAt("world",1,2).orElseThrow();
            assertEquals(p.getUniqueId(),claim.getOwner());assertEquals(1,claim.getChunkX());
            claims.getClaimAt(chunkAt(1,2)).setName("Home");
            assertTrue(claim.getName().isEmpty(),"a view does not change after it was taken");
            assertEquals(Optional.of("Home"),DyClaimAPI.getClaimAt("world",1,2).orElseThrow().getName());
            assertEquals(1,DyClaimAPI.getClaims(p.getUniqueId()).size());assertEquals(1,DyClaimAPI.getClaimCount(p.getUniqueId()));
            assertTrue(DyClaimAPI.getClaimAt("world",7,7).isEmpty());
            assertThrows(UnsupportedOperationException.class,()->claim.getTrustedPlayers().add(UUID.randomUUID()));
        }finally{field.set(null,old);}
    }
    @Test void createTransferTrustAndRemoveEventsFireAfterCommit(){
        bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
        List<Class<?>> fired=new ArrayList<>();var manager=plugin.getServer().getPluginManager();
        doAnswer(i->{Object event=i.getArgument(0);fired.add(event.getClass());
            if(event instanceof ClaimCreateEvent create){assertEquals(ClaimCreateEvent.Cause.PLAYER,create.getCause());assertNotNull(claims.getClaimAt("world",create.getClaim().getChunkX(),create.getClaim().getChunkZ()),"event sees the committed claim");}
            if(event instanceof ClaimRemoveEvent remove)assertNull(claims.getClaimAt("world",remove.getClaim().getChunkX(),remove.getClaim().getChunkZ()));
            return null;}).when(manager).callEvent(any());
        for(HandlerList handlers:List.of(ClaimCreateEvent.getHandlerList(),ClaimRemoveEvent.getHandlerList(),ClaimTransferEvent.getHandlerList(),ClaimTrustChangeEvent.getHandlerList()))
            handlers.register(new RegisteredListener(mock(Listener.class),(l,e)->{},EventPriority.NORMAL,mock(org.bukkit.plugin.Plugin.class),false));
        var c=chunkAt(0,0);var owner=player("Owner",c);var friend=player("Friend",c);
        claims.claimChunk(owner,c);
        features.handle(owner,new String[]{"trust","Friend"});
        features.handle(owner,new String[]{"untrust","Friend"});
        claims.transfer(claims.getClaimAt(c),friend);
        claims.remove(claims.getClaimAt(c).getChunkKey());
        assertEquals(List.of(ClaimCreateEvent.class,ClaimTrustChangeEvent.class,ClaimTrustChangeEvent.class,ClaimTransferEvent.class,ClaimRemoveEvent.class),fired);
    }
    @Test void noEventObjectsAreBuiltWithoutListeners(){
        var c=chunkAt(0,0);claims.claimChunk(player("Owner",c),c);
        verify(plugin.getServer().getPluginManager(),never()).callEvent(any());
    }

    // Claim messages
    @Test void ownersSetAndClearTheClaimMessageWithinLimits(){
        var c=chunkAt(0,0);var p=player("Owner",c);claims.claimChunk(p,c);
        features.handle(p,new String[]{"message","Welcome","to","my","base"});
        assertEquals("Welcome to my base",claims.getClaimAt(c).getGreeting());verify(p).sendMessage("message-set");
        features.handle(p,new String[]{"message","&cRed"});verify(p).sendMessage("message-invalid");
        features.handle(p,new String[]{"message","x".repeat(65)});verify(p,times(2)).sendMessage("message-invalid");
        assertEquals("Welcome to my base",claims.getClaimAt(c).getGreeting());
        features.handle(p,new String[]{"message","temizle"});
        assertNull(claims.getClaimAt(c).getGreeting());verify(p).sendMessage("message-cleared");
        values.set("claim-messages.enabled",false);clearInvocations(p);
        features.handle(p,new String[]{"message","Hi"});verify(p).sendMessage("feature-disabled");
    }
    @Test void visitorsSeeTheMessageWhenEnteringButOwnersDoNot(){
        var inside=chunkAt(1,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);claims.getClaimAt(inside).setGreeting("Hello &cthere");
        var visitor=player("Visitor",chunkAt(0,0));
        var listener=new dev.dyclaim.listener.PlayerListener(plugin);
        when(config.isShowEnter()).thenReturn(true);
        listener.onPlayerMove(new PlayerMoveEvent(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));
        verify(visitor).sendMessage("claim-greeting");
        verify(plugin.getMessageManager()).getMessage(eq(visitor),eq("claim-greeting"),argThat(m->"Hello cthere".equals(m.get("{message}"))));
        listener.onPlayerMove(new PlayerMoveEvent(owner,new Location(world,8,64,8),new Location(world,24,64,8)));
        verify(owner,never()).sendMessage("claim-greeting");
        values.set("claim-messages.enabled",false);clearInvocations(visitor);
        listener.onPlayerMove(new PlayerMoveEvent(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));
        verify(visitor,never()).sendMessage("claim-greeting");
    }
    @Test void ownerTagsApplyOnlyWhenColoursAreAllowed(){
        var inside=chunkAt(1,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);claims.getClaimAt(inside).setGreeting("<red>Hi &athere");
        var visitor=player("Visitor",chunkAt(0,0));
        var listener=new dev.dyclaim.listener.PlayerListener(plugin);
        values.set("claim-messages.allow-colors",true);
        listener.onPlayerMove(new PlayerMoveEvent(visitor,new Location(world,8,64,8),new Location(world,24,64,8)));
        verify(plugin.getMessageManager()).getMessage(eq(visitor),eq("claim-greeting"),argThat(m->"§cHi §athere".equals(m.get("{message}"))));
    }

    // MiniMessage
    @Test void miniMessageTagsWorkNextToAmpersandCodes(){
        assertEquals("§aHello",dev.dyclaim.manager.MessageManager.colorize("<green>Hello"));
        assertEquals("§7[§aDyClaim§7] §cNo",dev.dyclaim.manager.MessageManager.colorize("&7[&aDyClaim&7] <red>No"));
        assertTrue(dev.dyclaim.manager.MessageManager.colorize("<gradient:#ff0000:#0000ff>Gradient</gradient>").contains("§x"),"gradients become hex colours");
        assertEquals("1 < 2 and 3 > 2",dev.dyclaim.manager.MessageManager.colorize("1 < 2 and 3 > 2"),"text without tags is left alone");
        assertEquals("§cClaim: <red>x",dev.dyclaim.manager.MessageManager.colorize("<red>Claim: "+dev.dyclaim.manager.MessageManager.escapeTags("<red>x")),"tags typed by a player are shown, not applied");
        assertEquals("1 <2",dev.dyclaim.manager.MessageManager.colorize("1 "+dev.dyclaim.manager.MessageManager.escapeTags("<2")),"an escaped '<' without a tag is shown without the backslash");
    }

    // Visitor bans
    @Test void banningRemovesTrustAndKeepsThePlayerOutUntilUnbanned(){
        var inside=chunkAt(1,0);var owner=player("Owner",inside);var guest=visitorPlayer("Guest",chunkAt(0,0));claims.claimChunk(owner,inside);
        features.handle(owner,new String[]{"trust","Guest"});assertTrue(claims.getClaimAt(inside).isTrusted(guest.getUniqueId()));
        features.handle(owner,new String[]{"ban","Guest"});
        ClaimData claim=claims.getClaimAt(inside);
        assertTrue(claim.isVisitorBanned(guest.getUniqueId()));assertFalse(claim.isTrusted(guest.getUniqueId()));verify(owner).sendMessage("visitor-banned");
        var move=new PlayerMoveEvent(guest,new Location(world,8,64,8),new Location(world,24,64,8));
        features.entry(move);assertTrue(move.isCancelled());verify(guest).sendMessage("visitor-ban-entry");
        features.handle(owner,new String[]{"banlist"});verify(owner).sendMessage("visitor-ban-list-entry");
        features.handle(owner,new String[]{"unban","Guest"});assertFalse(claim.isVisitorBanned(guest.getUniqueId()));
        var again=new PlayerMoveEvent(guest,new Location(world,8,64,8),new Location(world,24,64,8));
        features.entry(again);assertFalse(again.isCancelled());
    }
    @Test void bansRespectExemptionsDurationsLimitsAndTheSwitch(){
        var inside=chunkAt(1,0);var owner=player("Owner",inside);var admin=player("Admin",chunkAt(0,0));claims.claimChunk(owner,inside);
        when(admin.hasPermission(anyString())).thenReturn(true);
        features.handle(owner,new String[]{"ban","Admin"});verify(owner).sendMessage("visitor-ban-exempt");
        features.handle(owner,new String[]{"ban","Owner"});verify(owner).sendMessage("visitor-ban-self");
        var guest=visitorPlayer("Guest",chunkAt(0,0));
        features.handle(owner,new String[]{"ban","Guest","kalıcı"});assertEquals(0L,claims.getClaimAt(inside).getVisitorBans().get(guest.getUniqueId()));
        features.handle(owner,new String[]{"ban","Guest","2h"});assertTrue(claims.getClaimAt(inside).getVisitorBans().get(guest.getUniqueId())>System.currentTimeMillis());
        features.handle(owner,new String[]{"trust","Guest"});assertFalse(claims.getClaimAt(inside).isVisitorBanned(guest.getUniqueId()),"trusting lifts a ban");
        values.set("claim-entry-bans.maximum-per-claim",1);
        features.handle(owner,new String[]{"ban","Guest"});visitorPlayer("Third",chunkAt(0,0));
        features.handle(owner,new String[]{"ban","Third"});verify(owner).sendMessage("visitor-ban-full");
        values.set("claim-entry-bans.enabled",false);
        var move=new PlayerMoveEvent(guest,new Location(world,8,64,8),new Location(world,24,64,8));
        features.entry(move);assertFalse(move.isCancelled(),"turning the feature off stops enforcement");
        clearInvocations(owner);features.handle(owner,new String[]{"ban","Guest"});verify(owner).sendMessage("feature-disabled");
    }
    @Test void transferClearsBansAndTheMessage(){
        var c=chunkAt(0,0);var owner=player("Owner",c);var buyer=player("Buyer",c);claims.claimChunk(owner,c);
        ClaimData claim=claims.getClaimAt(c);claim.setGreeting("Hi");claim.banVisitor(UUID.randomUUID(),0);
        claims.transfer(claim,buyer);
        assertNull(claim.getGreeting());assertTrue(claim.getVisitorBans().isEmpty());
    }
    @Test void bansAndMessagesSurviveSaveAndRejectCorruptValues() throws Exception {
        var store=new dev.dyclaim.manager.ClaimStore(dir.resolve("roundtrip.json"));
        var claim=new ClaimData(UUID.randomUUID(),"Owner","world",3,4);UUID banned=UUID.randomUUID();
        claim.setGreeting("Welcome");claim.banVisitor(banned,0);
        store.save(Map.of(claim.getChunkKey(),claim));
        ClaimData loaded=store.load(0).get("world:3:4");
        assertEquals("Welcome",loaded.getGreeting());assertTrue(loaded.isVisitorBanned(banned));
        claim.getVisitorBans().put(UUID.randomUUID(),-5L);
        assertThrows(java.io.IOException.class,()->store.save(Map.of(claim.getChunkKey(),claim)));
    }

    // Import
    @Test void importPlanSkipsTakenAndBlacklistedChunksAndKeepsTheFirstOwnerOnConflicts() throws Exception {
        var existing=chunkAt(0,0);claims.claimChunk(player("Existing",existing),existing);
        values.set("blacklisted-worlds",List.of("nether"));when(config.isWorldBlacklisted(anyString())).thenAnswer(i->values.getStringList("blacklisted-worlds").contains(i.getArgument(0)));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),friend=UUID.randomUUID();
        ClaimImporter.Source source=new ClaimImporter.Source(){
            public String id(){return "test";}
            public int read(List<ClaimImporter.Candidate> out){
                out.add(new ClaimImporter.Candidate(a,"Alice","world",0,0,Map.of()));
                out.add(new ClaimImporter.Candidate(a,"Alice","nether",5,5,Map.of()));
                out.add(new ClaimImporter.Candidate(a,"Alice","world",1,0,Map.of(friend,Set.of("build"))));
                out.add(new ClaimImporter.Candidate(a,"Alice","world",1,0,Map.of(friend,Set.of("containers"))));
                out.add(new ClaimImporter.Candidate(b,"Bob","world",1,0,Map.of()));
                out.add(new ClaimImporter.Candidate(b,"Bob","world",2,0,Map.of()));
                return 3;
            }
        };
        var importer=new ClaimImporter(plugin);var plan=importer.plan(source);
        assertEquals(3,plan.sourceClaims());assertEquals(2,plan.claims().size());
        assertEquals(1,plan.alreadyClaimed());assertEquals(1,plan.blacklisted());assertEquals(1,plan.conflicts());
        var result=importer.apply(plan);
        assertEquals(2,result.created());assertEquals(1,result.trusted());
        ClaimData merged=claims.getClaimAt("world",1,0);
        assertEquals(a,merged.getOwnerUUID());assertEquals(Set.of("build","containers"),merged.getTrustGrants().get(friend).permissions);
        assertEquals(0,claims.refund(merged),"imported claims carry no DyClaim purchase price");
        assertEquals(b,claims.getClaimAt("world",2,0).getOwnerUUID());
        assertTrue(java.nio.file.Files.list(dir.resolve("backups")).anyMatch(f->f.getFileName().toString().startsWith("import-test")));
    }
    @Test void rectangleClaimsCoverEveryTouchedChunk(){
        List<int[]> seen=new ArrayList<>();
        ClaimImporter.chunksOf("world",-1,10,17,15,(x,z)->seen.add(new int[]{x,z}));
        assertEquals(3,seen.size());
        assertArrayEquals(new int[]{-1,0},seen.get(0));assertArrayEquals(new int[]{1,0},seen.get(2));
    }
    public static class FakePos { public final String w;public final int x,z; public FakePos(String w,int x,int z){this.w=w;this.x=x;this.z=z;} public String world(){return w;} public int x(){return x;} public int z(){return z;} }
    public static class FakeData { public final UUID player;public final String lastIgn; public FakeData(UUID p,String n){player=p;lastIgn=n;} }
    public static class FakeChunks { final Map<UUID,FakePos[]> owned; FakeChunks(Map<UUID,FakePos[]> o){owned=o;} public FakePos[] getClaimedChunks(UUID id){return owned.getOrDefault(id,new FakePos[0]);} }
    public static class FakePlayers { final List<FakeData> joined;final Map<UUID,Map<String,Boolean>> access; FakePlayers(List<FakeData> j,Map<UUID,Map<String,Boolean>> a){joined=j;access=a;}
        public Collection<FakeData> getJoinedPlayers(){return joined;} public Map<UUID,Map<String,Boolean>> getAllPlayerPermissions(FakePos pos){return pos.z==0?access:Map.of();} }
    public static class FakeClaimChunk { final FakeChunks chunks;final FakePlayers players; FakeClaimChunk(FakeChunks c,FakePlayers p){chunks=c;players=p;} public FakeChunks getChunkHandler(){return chunks;} public FakePlayers getPlayerHandler(){return players;} }
    @Test void claimChunkDataIsReadThroughItsPublicHandlers(){
        UUID owner=UUID.randomUUID(),friend=UUID.randomUUID();
        var cc=new FakeClaimChunk(new FakeChunks(Map.of(owner,new FakePos[]{new FakePos("world",0,0),new FakePos("world",0,1)})),
                new FakePlayers(List.of(new FakeData(owner,"Carol")),Map.of(friend,Map.of("break",true,"doors",true,"useContainers",false))));
        List<ClaimImporter.Candidate> out=new ArrayList<>();
        assertEquals(2,new ClaimChunkImport(cc).read(out));
        assertEquals("Carol",out.get(0).ownerName());
        assertEquals(Set.of("build","doors","trapdoors"),out.get(0).trust().get(friend));
        assertTrue(out.get(1).trust().isEmpty());
        assertThrows(IllegalStateException.class,()->new ClaimChunkImport(new Object()).read(new ArrayList<>()));
    }
    @Test void griefPreventionClaimsBecomeChunksWithMappedTrust() throws Exception {
        var gp=mock(me.ryanhamshire.GriefPrevention.GriefPrevention.class);var store=mock(me.ryanhamshire.GriefPrevention.DataStore.class);
        gp.dataStore=store;var field=me.ryanhamshire.GriefPrevention.GriefPrevention.class.getField("instance");Object old=field.get(null);field.set(null,gp);
        try{
            UUID owner=UUID.randomUUID(),builder=UUID.randomUUID(),accessor=UUID.randomUUID();
            var claim=mock(me.ryanhamshire.GriefPrevention.Claim.class);
            when(claim.getOwnerID()).thenReturn(owner);when(claim.getOwnerName()).thenReturn("Dana");
            when(claim.getLesserBoundaryCorner()).thenReturn(new Location(world,0,0,0));when(claim.getGreaterBoundaryCorner()).thenReturn(new Location(world,20,0,5));
            doAnswer(i->{((List<String>)i.getArgument(0)).add(builder.toString());((List<String>)i.getArgument(0)).add("public");((List<String>)i.getArgument(2)).add(accessor.toString());return null;})
                    .when(claim).getPermissions(any(),any(),any(),any());
            var admin=mock(me.ryanhamshire.GriefPrevention.Claim.class);when(admin.isAdminClaim()).thenReturn(true);
            when(store.getClaims()).thenReturn(List.of(claim,admin));
            List<ClaimImporter.Candidate> out=new ArrayList<>();
            assertEquals(1,new dev.dyclaim.hook.GriefPreventionImport().read(out));
            assertEquals(2,out.size());assertEquals("Dana",out.get(0).ownerName());
            assertTrue(out.get(0).trust().get(builder).contains("build"));
            assertEquals(Set.of("doors","trapdoors","redstone"),out.get(0).trust().get(accessor));
        }finally{field.set(null,old);}
    }
    @Test void importCommandPreviewsFirstAndExplainsMissingSources(){
        var console=mock(org.bukkit.command.ConsoleCommandSender.class);when(console.hasPermission(anyString())).thenReturn(true);
        when(plugin.getMessageManager().getPrefixed(any(CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        var command=new dev.dyclaim.command.ClaimCommand(plugin);
        command.onCommand(console,null,"claim",new String[]{"admin","import","griefprevention"});
        verify(console).sendMessage("import-unavailable");
        command.onCommand(console,null,"claim",new String[]{"admin","import"});verify(console).sendMessage("import-usage");
        command.onCommand(console,null,"claim",new String[]{"admin","import","nothing"});verify(console).sendMessage("invalid-input");
        assertTrue(command.onTabComplete(console,null,"claim",new String[]{"admin","import",""}).containsAll(List.of("griefprevention","claimchunk")));
    }

    // Maps
    ClaimData claimOf(UUID owner,String world,int x,int z,String name){var c=new ClaimData(owner,"Owner",world,x,z);if(name!=null)c.setName(name);return c;}
    @Test void mapAreasJoinEachOwnersChunksIntoFewRectangles(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();List<ClaimData> list=new ArrayList<>();
        for(int x=0;x<3;x++)for(int z=0;z<2;z++)list.add(claimOf(a,"world",x,z,x==1&&z==1?"Base":null));
        list.add(claimOf(a,"world",0,2,null));
        list.add(claimOf(b,"world",3,0,null));
        list.add(claimOf(a,"hidden",9,9,null));
        var areas=ClaimAreas.of(list,Set.of("hidden"));
        assertEquals(3,areas.size(),areas.toString());
        var big=areas.stream().filter(r->r.owner().equals(a)&&r.maxX()==2).findFirst().orElseThrow();
        assertEquals(0,big.minZ());assertEquals(1,big.maxZ());assertEquals(List.of("Base"),big.names());
        assertEquals(48,big.maxBlockX());assertEquals(32,big.maxBlockZ());
        int covered=areas.stream().mapToInt(r->(r.maxX()-r.minX()+1)*(r.maxZ()-r.minZ()+1)).sum();
        assertEquals(8,covered,"every visible chunk is covered exactly once");
        assertEquals(ClaimAreas.color(a),ClaimAreas.color(a));
    }
    @Test void integrationsStayIdleWithoutMapOrPlaceholderPlugins(){
        var integrations=new dev.dyclaim.hook.Integrations(plugin);
        integrations.start();
        assertFalse(integrations.placeholdersActive());assertTrue(integrations.activeMaps().isEmpty());
        integrations.refreshMaps();integrations.reload();integrations.close();
    }
    @Test void backgroundSavesWriteTheNewestCopyOnly() throws Exception {
        var save=dev.dyclaim.manager.ClaimManager.class.getDeclaredMethod("saveInBackground");save.setAccessible(true);
        var first=chunkAt(0,0);claims.claimChunk(player("Owner",first),first);save.invoke(claims);
        var second=chunkAt(1,0);claims.claimChunk(player("Owner",second),second);save.invoke(claims);
        var writes=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler,times(2)).runTaskAsynchronously(eq(plugin),writes.capture());
        var store=new dev.dyclaim.manager.ClaimStore(dir.resolve("claims.json"));
        writes.getAllValues().get(1).run();
        assertEquals(2,store.load(0).size(),"the newer copy is written");
        writes.getAllValues().get(0).run();
        assertEquals(2,store.load(0).size(),"an older copy that finishes later must not replace it");
        var third=chunkAt(2,0);claims.claimChunk(player("Owner",third),third);claims.saveAllSync();
        assertEquals(3,store.load(0).size(),"saveAllSync still writes right away");
    }
    @SuppressWarnings("unchecked")
    @Test void mapRefreshLeavesUnchangedMapsAlone() throws Exception {
        when(plugin.getMessageManager().getMessage(anyString(),anyMap())).thenAnswer(i->i.getArgument(0));
        List<String> calls=new ArrayList<>();boolean[] lost={false};
        var layer=new dev.dyclaim.hook.map.MapLayer(){
            public String name(){return "Fake";}
            public void update(List<ClaimAreas.Area> areas,Style style,Labels labels){calls.add("all "+areas.size());lost[0]=false;}
            public void change(List<ClaimAreas.Area> removed,List<ClaimAreas.Area> added,Style style,Labels labels){calls.add("change -"+removed.size()+" +"+added.size());}
            public boolean lost(){return lost[0];}
            public void close(){}
        };
        var integrations=new dev.dyclaim.hook.Integrations(plugin);
        var field=dev.dyclaim.hook.Integrations.class.getDeclaredField("maps");field.setAccessible(true);
        ((List<dev.dyclaim.hook.map.MapLayer>)field.get(integrations)).add(layer);
        var first=chunkAt(0,0);claims.claimChunk(player("Owner",first),first);
        integrations.refreshMaps();integrations.refreshMaps();
        assertEquals(List.of("all 1"),calls,"an unchanged refresh must not touch the map");
        var second=chunkAt(5,5);claims.claimChunk(player("Owner",second),second);
        integrations.refreshMaps();
        assertEquals(List.of("all 1","change -0 +1"),calls,"a new claim sends only that area");
        var next=chunkAt(1,0);claims.claimChunk(player("Owner",next),next);
        integrations.refreshMaps();
        assertEquals("change -1 +1",calls.get(2),"a claim next to an area replaces that one area");
        lost[0]=true;integrations.refreshMaps();
        assertEquals("all 2",calls.get(3),"a map plugin that lost DyClaim's markers gets everything again");
        values.set("integrations.maps.layer-name","Land");integrations.refreshMaps();
        assertEquals(List.of("all 1","change -0 +1","change -1 +1","all 2","all 2"),calls,"a style change redraws the map");
    }

    // PlaceholderAPI
    @Test void placeholdersReportCountsLimitsAndTheCurrentClaim(){
        var c=chunkAt(0,0);var owner=player("Owner",c);claims.claimChunk(owner,c);claims.getClaimAt(c).setName("Base");
        when(owner.getPlayer()).thenReturn(owner);
        Location at=new Location(world,3,64,3);when(owner.getLocation()).thenReturn(at);
        var hook=new dev.dyclaim.hook.PlaceholderHook(plugin);
        assertEquals("1",hook.onRequest(owner,"claims"));assertEquals("10",hook.onRequest(owner,"limit"));assertEquals("9",hook.onRequest(owner,"remaining"));
        assertEquals("Owner",hook.onRequest(owner,"owner"));assertEquals("Base",hook.onRequest(owner,"name"));
        assertEquals("true",hook.onRequest(owner,"in_claim"));assertEquals("owner",hook.onRequest(owner,"role"));assertEquals("true",hook.onRequest(owner,"can_build"));
        assertEquals("1",hook.onRequest(null,"total"));assertNull(hook.onRequest(owner,"unknown"));
        grant(owner,"dyclaim.limit.unlimited");assertEquals("∞",hook.onRequest(owner,"limit"));
        var visitor=player("Visitor",c);when(visitor.getPlayer()).thenReturn(visitor);when(visitor.getLocation()).thenReturn(at);
        assertEquals("visitor",hook.onRequest(visitor,"role"));assertEquals("false",hook.onRequest(visitor,"can_build"));
        UUID ownerId=owner.getUniqueId();var offline=mock(org.bukkit.OfflinePlayer.class);when(offline.getUniqueId()).thenReturn(ownerId);
        assertEquals("1",hook.onRequest(offline,"claims"));assertEquals("",hook.onRequest(offline,"owner"));
    }

    // Languages and resources
    YamlConfiguration resource(String name) throws Exception {var y=new YamlConfiguration();y.loadFromString(new String(getClass().getResourceAsStream("/"+name).readAllBytes(),StandardCharsets.UTF_8));return y;}
    @Test void everyLanguageHasTheSameKeysPlaceholdersAndHelpFormat() throws Exception {
        var en=resource("messages_en.yml");Pattern placeholder=Pattern.compile("\\{[^}]+}");
        for(String lang:dev.dyclaim.manager.MessageManager.LANGUAGES){
            var other=resource("messages_"+lang+".yml");
            assertEquals(en.getKeys(true),other.getKeys(true),lang);
            for(String key:en.getKeys(true)){
                Set<String> a=new HashSet<>(),b=new HashSet<>();var m=placeholder.matcher(en.getString(key));while(m.find())a.add(m.group());var n=placeholder.matcher(other.getString(key));while(n.find())b.add(n.group());
                assertEquals(a,b,lang+":"+key);
                if(key.startsWith("help-")&&!Set.of("help-header","help-footer","help-page","help-page-next").contains(key))assertTrue(other.getString(key).matches("&6/[^\\r\\n]+ &7- .+"),lang+":"+key);
            }
        }
    }
    @Test void clientLocaleAndConfiguredLanguageSelectTheNewCatalogs() throws Exception {
        when(plugin.getResource(anyString())).thenAnswer(i->getClass().getResourceAsStream("/"+i.getArgument(0)));
        // A real plugin copies bundled files on first start; the mock does the same into the test folder.
        doAnswer(i->{String name=i.getArgument(0);try(var in=getClass().getResourceAsStream("/"+name)){java.nio.file.Files.copy(in,dir.resolve(name));}return null;}).when(plugin).saveResource(anyString(),anyBoolean());
        when(config.getLang()).thenReturn("auto");
        var messages=new dev.dyclaim.manager.MessageManager(plugin);
        var german=player("German",chunkAt(0,0));when(german.getLocale()).thenReturn("de_de");
        var russian=player("Russian",chunkAt(0,0));when(russian.getLocale()).thenReturn("ru_ru");
        var french=player("French",chunkAt(0,0));when(french.getLocale()).thenReturn("fr_fr");
        assertEquals("de",messages.getPreferredLanguage(german));assertEquals("ru",messages.getPreferredLanguage(russian));assertEquals("en",messages.getPreferredLanguage(french));
        assertTrue(messages.getMessage(german,"no-permission").contains("Berechtigung"));
        assertTrue(java.nio.file.Files.exists(dir.resolve("messages_es.yml")),"new language files are written on first start");
        when(config.getLang()).thenReturn("es");
        assertEquals("es",messages.getPreferredLanguage(german));
    }
    @Test void configAcceptsTheNewLanguagesAndRejectsBadMapSettings() throws Exception {
        var defaults=resource("config.yml");
        for(String lang:List.of("auto","en","tr","de","es","ru")){var c=resource("config.yml");c.set("lang",lang);c.setDefaults(defaults);dev.dyclaim.manager.ConfigManager.validate(c);}
        var bad=resource("config.yml");bad.set("lang","fr");bad.setDefaults(defaults);
        assertThrows(IllegalArgumentException.class,()->dev.dyclaim.manager.ConfigManager.validate(bad));
        var opacity=resource("config.yml");opacity.set("integrations.maps.fill-opacity",1.5);opacity.setDefaults(defaults);
        assertThrows(IllegalArgumentException.class,()->dev.dyclaim.manager.ConfigManager.validate(opacity));
        var refresh=resource("config.yml");refresh.set("integrations.maps.refresh-seconds",1);refresh.setDefaults(defaults);
        assertThrows(IllegalArgumentException.class,()->dev.dyclaim.manager.ConfigManager.validate(refresh));
    }
    @Test void descriptorDeclaresTheNewPermissionsAndOptionalPlugins() throws Exception {
        var yaml=resource("plugin.yml");
        assertTrue(yaml.getStringList("softdepend").containsAll(List.of("PlaceholderAPI","BlueMap","dynmap","squaremap")));
        assertTrue(yaml.getBoolean("permissions.dyclaim.player.children.dyclaim.message"));
        assertTrue(yaml.getBoolean("permissions.dyclaim.player.children.dyclaim.ban"));
        assertEquals("op",yaml.getString("permissions.dyclaim.ban.exempt.default"));
    }
    @Test void bStatsIsRelocatedAndItsLicenseShips() throws Exception {
        Class<?> base=Class.forName("dev.dyclaim.lib.bstats.MetricsBase");
        assertEquals("dev.dyclaim.lib.bstats",base.getPackageName());
        assertThrows(ClassNotFoundException.class,()->Class.forName("org.bstats.MetricsBase"));
        assertTrue(new String(getClass().getResourceAsStream("/licenses/bstats-LICENSE.txt").readAllBytes(),StandardCharsets.UTF_8).contains("MIT License"));
        assertEquals("0",DyClaim.claimRange(0));assertEquals("1-10",DyClaim.claimRange(10));assertEquals("201-1000",DyClaim.claimRange(1000));assertEquals("1000+",DyClaim.claimRange(1001));
    }
    @Test void helpShowsTheNewRowsOnlyWhenTheirFeatureIsOn(){
        when(plugin.getMessageManager().getPrefix()).thenReturn("[DyClaim]");
        var p=player("Owner",chunkAt(0,0));var command=new dev.dyclaim.command.ClaimCommand(plugin);
        List<String> rows=new ArrayList<>();
        for(int page=1;page<=6;page++){clearInvocations(p);command.onCommand(p,null,"claim",new String[]{"help",String.valueOf(page)});
            var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(p,atLeastOnce()).sendMessage(captor.capture());rows.addAll(captor.getAllValues());}
        assertTrue(rows.containsAll(List.of("help-message","help-ban","help-unban","help-banlist")));
        values.set("claim-messages.enabled",false);values.set("claim-entry-bans.enabled",false);rows.clear();
        for(int page=1;page<=6;page++){clearInvocations(p);command.onCommand(p,null,"claim",new String[]{"help",String.valueOf(page)});
            var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(p,atLeastOnce()).sendMessage(captor.capture());rows.addAll(captor.getAllValues());}
        assertFalse(rows.contains("help-message"));assertFalse(rows.contains("help-ban"));
    }
}
