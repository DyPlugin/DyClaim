package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.*;
import dev.dyclaim.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Command features share the existing managers rather than creating a second claim model. */
public class FeatureManager implements Listener {
    public static class Seen { public long at; public boolean exempt; }
    public static class WarningChain { public List<Long> times=new ArrayList<>(); public String stage="pending"; public long until; public String mode; public UUID target; public String name; }
    public static class State {
        public int version=1;
        public Map<UUID,Seen> lastSeen=new HashMap<>();
        public Map<UUID,Long> bans=new HashMap<>();
        public Map<String,WarningChain> warnings=new HashMap<>();
        public Map<UUID,String> languages=new HashMap<>();
    }
    private final DyClaim plugin;
    private final Path stateFile;
    private final State state;
    private final Set<UUID> auto=new HashSet<>();
    private final Map<UUID,Long> autoAttempt=new HashMap<>();
    private BukkitTask mobTask,purgeTask;
    private Iterator<String> mobClaims=Collections.emptyIterator();
    private Iterator<Entity> mobEntities=Collections.emptyIterator();
    private ClaimData scanningClaim;
    public FeatureManager(DyClaim plugin) {
        this.plugin=plugin;stateFile=plugin.getDataFolder().toPath().resolve("state.json");
        try{state=Files.exists(stateFile)?AtomicJson.read(stateFile,State.class):new State();}
        catch(IOException ex){throw new IllegalStateException("Invalid state data; writes disabled",ex);}
        if(state.version!=1||state.bans==null||state.warnings==null||state.languages==null||state.lastSeen==null)throw new IllegalStateException("Invalid state schema");
        for(var entry:state.bans.entrySet())if(entry.getKey()==null||entry.getValue()==null||entry.getValue()<0)throw new IllegalStateException("Invalid ban");
        for(var chain:state.warnings.values())if(chain==null||chain.times==null||chain.stage==null||chain.until<0)throw new IllegalStateException("Invalid warning");
        for(var seen:state.lastSeen.values())if(seen==null||seen.at<0)throw new IllegalStateException("Invalid activity record");
        for(Player player:Bukkit.getOnlinePlayers())recordSeen(player);
    }
    public void save(){try{AtomicJson.write(stateFile,state);}catch(IOException ex){plugin.getServer().getPluginManager().disablePlugin(plugin);throw new IllegalStateException("State write failed",ex);}}
    public String language(UUID uuid){return state.languages.get(uuid);}
    private long now(){return System.currentTimeMillis();}
    private boolean enabled(String key){return plugin.getConfigManager().values().getBoolean(key);}
    public boolean isClaimBanned(UUID uuid){Long expiry=state.bans.get(uuid);return enabled("admin.claim-bans.enabled")&&expiry!=null&&(expiry==0||expiry>now());}
    public void send(Player p,String key){p.sendMessage(plugin.getMessageManager().getPrefixed(p,key));}
    private void send(Player p,String key,Map<String,String> values){p.sendMessage(plugin.getMessageManager().getPrefixed(p,key,values));}
    private void display(Player p,String key){p.sendMessage(plugin.getMessageManager().getMessage(p,key));}
    private void display(Player p,String key,Map<String,String> values){p.sendMessage(plugin.getMessageManager().getMessage(p,key,values));}
    private boolean permission(Player p,String node){if(p.hasPermission(node))return true;send(p,"no-permission");return false;}
    private ClaimData owned(Player p,boolean primary) {
        ClaimData claim=plugin.getClaimManager().getClaimAt(p.getLocation().getChunk());
        if(claim==null){send(p,"sell-not-claimed");return null;}
        if(!(primary?claim.getOwnerUUID().equals(p.getUniqueId()):claim.canManage(p.getUniqueId()))){send(p,"sell-not-owner");return null;}
        if(plugin.getTransactionManager().isLocked(claim.getChunkKey())){send(p,"transaction-locked");return null;}
        return claim;
    }
    public void invalidate(ClaimData claim) {
        plugin.getConfirmationManager().cancelClaim(claim.getChunkKey());
        state.warnings.keySet().removeIf(key->key.startsWith(claim.getId()+":"));save();
    }
    public boolean handle(Player p,String[] args) {
        args=dev.dyclaim.util.CommandWords.normalize(args);
        if(args.length==0){requestClaim(p);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        try {
            switch(sub) {
                case "coowner","ortak" -> coowner(p,args);
                case "transfer","devret" -> transfer(p,args);
                case "market","pazar" -> market(p,args);
                case "auto","otomatik" -> auto(p,args);
                case "name","isim" -> name(p,args);
                case "setspawn","spawnayarla" -> spawn(p);
                case "trustperm","guvenizin","güvenizin" -> trustPermission(p,args);
                case "trust","guven","güven" -> trust(p,args);
                case "untrust","guvensil","güvensil" -> untrust(p,args);
                case "trustlist","guvenliste","güvenliste" -> trustList(p);
                case "villager","koylu","köylü" -> toggle(p,"villager-player-damage");
                case "doors","kapi","kapı" -> toggle(p,"visitor-doors");
                case "trapdoors","tuzakkapisi","tuzakkapısı" -> toggle(p,"visitor-trapdoors");
                case "pvp" -> toggle(p,"pvp");
                case "explosion","patlama" -> toggle(p,"explosions");
                case "mob" -> toggle(p,"mob-spawning");
                case "mobexplosion","mobpatlama" -> toggle(p,"mob-explosions");
                case "warn","uyar" -> warn(p,args);
                case "lang","dil" -> language(p,args);
                case "tp","isinlan","ışınlan" -> teleport(p,args);
                case "info","bilgi" -> info(p);
                case "list","liste" -> list(p,args);
                case "admin" -> {return admin(p,args);}
                default -> {return false;}
            }
        } catch(IllegalArgumentException ex){send(p,"invalid-input");}
        return true;
    }
    public void requestClaim(Player player) {
        if(!permission(player,"dyclaim.claim"))return;
        Chunk chunk=player.getLocation().getChunk();
        ClaimData existing=plugin.getClaimManager().getClaimAt(chunk);
        if(existing!=null){if(existing.getOwnerUUID().equals(player.getUniqueId()))send(player,"claim-already-owned");else send(player,"claim-already-claimed",Map.of("{owner}",existing.getOwnerName()));return;}
        if(!eligibleNew(player,chunk,true))return;
        double price=plugin.getEconomyManager().isEnabled()?plugin.getAcquisitionRules().price(chunk.getWorld().getName()):0;
        boolean paid=plugin.getEconomyManager().isEnabled();
        send(player,paid?"claim-confirmation":"claim-free",Map.of("{price}",plugin.getEconomyManager().formatMoney(price)));
        Runnable execute=()->{
            if(!player.isOnline()||!player.hasPermission("dyclaim.claim")||paid!=plugin.getEconomyManager().isEnabled()
                    ||Double.compare(price,paid?plugin.getAcquisitionRules().price(chunk.getWorld().getName()):0)!=0){send(player,"operation-changed");return;}
            acquire(player,chunk,price,true);
        };
        if(enabled("confirmations.required.claim"))plugin.getConfirmationManager().addPending(player.getUniqueId(),ConfirmationManager.ActionType.CLAIM,plugin.getClaimManager().getChunkKey(chunk),uuid->execute.run(),uuid->send(player,"confirm-deny"));
        else execute.run();
    }
    private boolean eligibleNew(Player player,Chunk chunk,boolean notify) {
        String error=plugin.getAcquisitionRules().check(player,chunk,null,false);
        if(error==null&&plugin.getCooldownManager().hasCooldown(player.getUniqueId()))error="acquire-cooldown";
        if(error!=null){if(notify)send(player,error);return false;}
        double price=plugin.getEconomyManager().isEnabled()?plugin.getAcquisitionRules().price(chunk.getWorld().getName()):0;
        if(price>0&&!plugin.getEconomyManager().hasEnough(player,price)){if(notify)send(player,"claim-no-money",Map.of("{price}",plugin.getEconomyManager().formatMoney(price)));return false;}
        return true;
    }
    private boolean acquire(Player p,Chunk chunk,double price,boolean notify) {
        if(!eligibleNew(p,chunk,notify))return false;
        boolean success=plugin.getTransactionManager().execute("claim",plugin.getClaimManager().getChunkKey(chunk),p.getUniqueId(),null,price,0,()->{
            if(!plugin.getClaimManager().claimChunk(p,chunk))throw new IllegalStateException("Chunk occupied");
        });
        if(!success){send(p,"transaction-failed");return false;}
        plugin.getCooldownManager().setCooldown(p.getUniqueId());
        send(p,"claim-success",Map.of("{chunk}",chunk.getX()+", "+chunk.getZ()));
        if(enabled("visualization.show-after-claim")&&(!auto.contains(p.getUniqueId())||enabled("auto-claim.show-borders")))plugin.getChunkVisualizer().showChunkBorders(p,chunk);
        return true;
    }
    private void toggle(Player p,String key) {
        if(!permission(p,"dyclaim.settings"))return;
        ClaimData c=owned(p,false);if(c==null)return;
        ProtectionPolicy.Value value=plugin.getAccessManager().policy(c,key);
        if(value.locked()){send(p,"setting-locked");return;}
        c.setChoice(key,!value.enabled());plugin.getClaimManager().saveAll();
        send(p,"setting-updated",Map.of("{setting}",plugin.getMessageManager().getMessage(p,"setting-"+key),"{value}",plugin.getMessageManager().getMessage(p,!value.enabled()?"state-enabled":"state-disabled")));
    }
    private UUID known(String name) {
        Player online=Bukkit.getPlayerExact(name);if(online!=null)return online.getUniqueId();
        for(OfflinePlayer offline:Bukkit.getOfflinePlayers())if(name.equalsIgnoreCase(offline.getName()))return offline.getUniqueId();
        return null;
    }
    private long duration(String input,boolean unlimited) {
        return Rules.expiry(input,now(),unlimited?Long.MAX_VALUE:plugin.getConfigManager().values().getLong("trust.maximum-duration-days")*86_400_000L,true);
    }
    private void trust(Player p,String[] args) {
        if(!permission(p,"dyclaim.trust"))return;
        ClaimData c=owned(p,false);if(c==null)return;
        if(args.length<2){send(p,"trust-usage");return;}
        long expiry=args.length>=3?duration(args[2],false):0;
        if(expiry!=0&&!enabled("trust.temporary-enabled")){send(p,"feature-disabled");return;}
        if(args[1].startsWith("clan:")) {
            String clan=plugin.getClanTrustManager().resolve(args[1].substring(5));
            if(clan==null){send(p,"clan-unavailable");return;}
            c.getClanGrants().put(clan,new TrustGrant(expiry));c.touch();
        }else {
            UUID target=known(args[1]);if(target==null){send(p,"admin-player-not-found");return;}
            if(c.canManage(target)){send(p,"trust-self");return;}
            c.grant(target,expiry);
        }
        invalidate(c);plugin.getClaimManager().saveAll();send(p,"trust-added",Map.of("{player}",args[1]));
    }
    private void untrust(Player p,String[] args) {
        if(!permission(p,"dyclaim.trust"))return;ClaimData c=owned(p,false);if(c==null)return;
        if(args.length<2){send(p,"untrust-usage");return;}
        boolean removed;
        if(args[1].startsWith("clan:")){String id=plugin.getClanTrustManager().resolve(args[1].substring(5));removed=c.getClanGrants().remove(id)!=null;}
        else {UUID target=known(args[1]);removed=target!=null&&c.removeTrusted(target);}
        if(!removed){send(p,"trust-not-found",Map.of("{player}",args[1]));return;}
        c.touch();invalidate(c);plugin.getClaimManager().saveAll();send(p,"trust-removed",Map.of("{player}",args[1]));
    }
    private void trustPermission(Player p,String[] args) {
        if(!permission(p,"dyclaim.trust")||!enabled("trust.granular-permissions-enabled")){send(p,"feature-disabled");return;}
        ClaimData c=owned(p,false);if(c==null)return;
        if(args.length!=4||!TrustGrant.RIGHTS.contains(args[2])||!Set.of("allow","deny").contains(args[3])){send(p,"trustperm-usage");return;}
        UUID uuid=known(args[1]);TrustGrant grant=c.getTrustGrants().get(uuid);
        if(grant==null||!(grant.expiresAt==0||grant.expiresAt>now())){send(p,"trust-not-found",Map.of("{player}",args[1]));return;}
        if(args[3].equals("allow"))grant.permissions.add(args[2]);else grant.permissions.remove(args[2]);
        c.touch();invalidate(c);plugin.getClaimManager().saveAll();send(p,"operation-success");
    }
    private void trustList(Player p) {
        if(!permission(p,"dyclaim.trust"))return;ClaimData c=owned(p,false);if(c==null)return;
        display(p,"trust-list-header");int count=0;
        for(var entry:c.getTrustGrants().entrySet()) {
            if(entry.getValue().expiresAt!=0&&entry.getValue().expiresAt<=now())continue;
            if(count++>=20)break;
            String name=Bukkit.getOfflinePlayer(entry.getKey()).getName();
            display(p,"trust-list-entry",Map.of("{player}",name==null?entry.getKey().toString():name));
            if(entry.getValue().expiresAt!=0||!entry.getValue().permissions.containsAll(TrustGrant.RIGHTS))
                display(p,"trust-list-details",Map.of("{rights}",String.join(", ",entry.getValue().permissions.stream().sorted().map(right->dev.dyclaim.util.CommandWords.display(right,"tr".equals(plugin.getMessageManager().getPreferredLanguage(p)))).toList()),"{expiry}",entry.getValue().expiresAt==0?dev.dyclaim.util.CommandWords.display("permanent","tr".equals(plugin.getMessageManager().getPreferredLanguage(p))):displayDate(entry.getValue().expiresAt)));
        }
        for(String clan:c.getClanGrants().keySet())if(count++<20)display(p,"trust-list-clan",Map.of("{clan}",clan));
        if(count==0)display(p,"trust-list-empty");
    }
    private boolean unchanged(ClaimData c,UUID id,long revision,UUID owner) {
        ClaimData actual=plugin.getClaimManager().getByKey(c.getChunkKey());
        return actual==c&&c.getId().equals(id)&&c.getRevision()==revision&&c.getOwnerUUID().equals(owner)&&!plugin.getTransactionManager().isLocked(c.getChunkKey());
    }
    private void coowner(Player p,String[] args) {
        if(!permission(p,"dyclaim.coowner")||!enabled("ownership.coowner-enabled")){send(p,"feature-disabled");return;}
        ClaimData c=owned(p,true);if(c==null)return;
        if(args.length<2){send(p,"coowner-usage");return;}
        if(args[1].equals("remove")) {
            c.setCoowner(null);plugin.getClaimManager().rebuildPlayerIndex();invalidate(c);plugin.getClaimManager().saveAll();send(p,"operation-success");return;
        }
        if(args.length!=3||!args[1].equals("add")){send(p,"coowner-usage");return;}
        Player target=Bukkit.getPlayerExact(args[2]);
        if(target==null||target==p){send(p,"admin-player-not-found");return;}
        if(c.getCoowner()!=null){send(p,"coowner-full");return;}
        UUID owner=c.getOwnerUUID(),id=c.getId();long revision=c.getRevision();
        String error=plugin.getAcquisitionRules().check(target,p.getLocation().getChunk(),c,true);
        if(error!=null){send(p,error);return;}
        send(target,"coowner-offer",Map.of("{owner}",p.getName(),"{chunk}",c.getChunkKey()));
        plugin.getConfirmationManager().addPending(target.getUniqueId(),ConfirmationManager.ActionType.COOWNER,c.getChunkKey(),uuid->{
            if(!p.isOnline()||!target.isOnline()||!p.hasPermission("dyclaim.coowner")||!enabled("ownership.coowner-enabled")||!unchanged(c,id,revision,owner)){send(target,"operation-changed");return;}
            World world=Bukkit.getWorld(c.getWorld());if(world==null){send(target,"operation-changed");return;}
            String rejection=plugin.getAcquisitionRules().check(target,world.getChunkAt(c.getChunkX(),c.getChunkZ()),c,true);
            if(rejection!=null){send(target,rejection);return;}
            c.setCoowner(target.getUniqueId());plugin.getClaimManager().rebuildPlayerIndex();invalidate(c);plugin.getClaimManager().saveAllSync();send(target,"operation-success");send(p,"operation-success");
        },uuid->send(target,"confirm-deny"));send(p,"offer-sent");
    }
    private void transfer(Player p,String[] args) {
        if(!permission(p,"dyclaim.transfer")||!enabled("ownership.transfer-enabled")){send(p,"feature-disabled");return;}
        ClaimData c=owned(p,true);if(c==null)return;
        if(args.length!=2){send(p,"transfer-usage");return;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null||target==p){send(p,"admin-player-not-found");return;}
        if(c.getMarketPrice()!=null){send(p,"cancel-market-first");return;}
        String error=plugin.getAcquisitionRules().check(target,p.getLocation().getChunk(),c,false);if(error!=null){send(p,error);return;}
        UUID owner=c.getOwnerUUID(),id=c.getId();long revision=c.getRevision();
        send(target,"transfer-offer",Map.of("{owner}",p.getName(),"{chunk}",c.getChunkKey()));
        // Recipient consent is mandatory even when clickable buttons are hidden.
        plugin.getConfirmationManager().addPending(target.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,c.getChunkKey(),uuid->{
            if(!p.isOnline()||!target.isOnline()||!p.hasPermission("dyclaim.transfer")||!enabled("ownership.transfer-enabled")||!unchanged(c,id,revision,owner)){send(target,"operation-changed");return;}
            World world=Bukkit.getWorld(c.getWorld());if(world==null){send(target,"operation-changed");return;}
            String reject=plugin.getAcquisitionRules().check(target,world.getChunkAt(c.getChunkX(),c.getChunkZ()),c,false);
            if(reject!=null){send(target,reject);return;}
            plugin.getClaimManager().transfer(c,target);plugin.getClaimManager().saveAllSync();send(target,"operation-success");send(p,"operation-success");
        },uuid->send(target,"confirm-deny"));send(p,"offer-sent");
    }
    private void market(Player p,String[] args) {
        if(!permission(p,"dyclaim.market")||!enabled("market.enabled")){send(p,"feature-disabled");return;}
        if(!plugin.getEconomyManager().isEnabled()){send(p,"economy-required");return;}
        if(args.length<2){send(p,"market-usage");return;}
        if(args[1].equals("list")||args[1].equals("cancel")) {
            ClaimData c=owned(p,true);if(c==null)return;
            if(args[1].equals("cancel"))c.setMarketPrice(null);
            else {if(args.length!=3){send(p,"market-usage");return;}c.setMarketPrice(Rules.marketPrice(args[2],plugin.getConfigManager().values().getDouble("market.minimum-price"),plugin.getConfigManager().values().getDouble("market.maximum-price")));}
            invalidate(c);plugin.getClaimManager().saveAllSync();send(p,"operation-success");return;
        }
        if(!args[1].equals("buy")){send(p,"market-usage");return;}
        ClaimData c=plugin.getClaimManager().getClaimAt(p.getLocation().getChunk());
        if(c==null||c.getMarketPrice()==null||c.getOwnerUUID().equals(p.getUniqueId())){send(p,"market-unavailable");return;}
        String error=plugin.getAcquisitionRules().check(p,p.getLocation().getChunk(),c,false);if(error!=null){send(p,error);return;}
        double price=c.getMarketPrice(),tax=plugin.getConfigManager().values().getDouble("market.tax-percent");
        double income=Rules.money(price-Rules.money(price*tax/100));
        UUID owner=c.getOwnerUUID(),id=c.getId();long revision=c.getRevision();
        send(p,"market-confirm",Map.of("{price}",plugin.getEconomyManager().formatMoney(price),"{income}",plugin.getEconomyManager().formatMoney(income),"{chunk}",c.getChunkKey()));
        Runnable buy=()->{
            if(!p.isOnline()||!p.hasPermission("dyclaim.market")||!enabled("market.enabled")||!plugin.getEconomyManager().isEnabled()||!unchanged(c,id,revision,owner)
                    ||Double.compare(tax,plugin.getConfigManager().values().getDouble("market.tax-percent"))!=0){send(p,"operation-changed");return;}
            World world=Bukkit.getWorld(c.getWorld());if(world==null){send(p,"operation-changed");return;}
            String rejected=plugin.getAcquisitionRules().check(p,world.getChunkAt(c.getChunkX(),c.getChunkZ()),c,false);
            if(rejected!=null){send(p,rejected);return;}
            if(!plugin.getEconomyManager().hasEnough(p,price)){send(p,"claim-no-money",Map.of("{price}",plugin.getEconomyManager().formatMoney(price)));return;}
            if(plugin.getTransactionManager().execute("market",c.getChunkKey(),p.getUniqueId(),owner,price,income,()->plugin.getClaimManager().transfer(c,p))) {
                send(p,"operation-success");Player seller=Bukkit.getPlayer(owner);if(seller!=null)send(seller,"operation-success");
            }else send(p,"transaction-failed");
        };
        if(enabled("confirmations.required.market-purchase"))plugin.getConfirmationManager().addPending(p.getUniqueId(),ConfirmationManager.ActionType.MARKET,c.getChunkKey(),uuid->buy.run(),uuid->send(p,"confirm-deny"));else buy.run();
    }
    private void auto(Player p,String[] args) {
        if(!permission(p,"dyclaim.auto"))return;
        boolean turnOn=args.length==1?!auto.contains(p.getUniqueId()):parseToggle(args[1]);
        if(turnOn&&(!enabled("auto-claim.enabled")||(enabled("auto-claim.require-economy")&&!plugin.getEconomyManager().isEnabled())
                ||(!enabled("auto-claim.allow-zero-price")&&(!plugin.getEconomyManager().isEnabled()||plugin.getAcquisitionRules().price(p.getWorld().getName())==0)))){send(p,"auto-unavailable");return;}
        if(turnOn)auto.add(p.getUniqueId());else auto.remove(p.getUniqueId());send(p,turnOn?"auto-enabled":"auto-disabled");
    }
    private boolean parseToggle(String value){return switch(value.toLowerCase(Locale.ROOT)){case "on","enable","ac","aç"->true;case "off","disable","kapat"->false;default->throw new IllegalArgumentException("toggle");};}
    private void name(Player p,String[] args) {
        if(!permission(p,"dyclaim.name"))return;ClaimData c=owned(p,false);if(c==null)return;
        String text=String.join(" ",Arrays.copyOfRange(args,1,args.length)).trim();
        if(text.isBlank()||text.length()>plugin.getConfigManager().values().getInt("names.maximum-length")||text.matches("[0-9]+")||text.contains("&")||text.contains("§")||text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("name");
        if(plugin.getClaimManager().getPlayerClaims(c.getOwnerUUID()).stream().anyMatch(other->other!=c&&text.equalsIgnoreCase(other.getName()))){send(p,"name-duplicate");return;}
        c.setName(text);plugin.getClaimManager().saveAll();send(p,"operation-success");
    }
    private void spawn(Player p) {
        if(!permission(p,"dyclaim.setspawn"))return;ClaimData c=owned(p,false);if(c==null)return;
        Location l=p.getLocation();if(!TeleportManager.safe(l)){send(p,"tp-unsafe");return;}
        c.setSpawn(l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch());plugin.getClaimManager().saveAll();send(p,"operation-success");
    }
    private void teleport(Player p,String[] args) {
        if(!permission(p,"dyclaim.teleport"))return;
        List<ClaimData> claims=plugin.getClaimManager().getPlayerClaims(p.getUniqueId());if(claims.isEmpty()){send(p,"tp-no-claims");return;}
        if(args.length<2){send(p,"tp-usage",Map.of("{max}",String.valueOf(claims.size())));return;}
        ClaimData destination=null;String selector=String.join(" ",Arrays.copyOfRange(args,1,args.length));
        try{int index=Integer.parseInt(selector)-1;if(index>=0&&index<claims.size())destination=claims.get(index);}catch(NumberFormatException ignored){for(ClaimData c:claims)if(selector.equalsIgnoreCase(c.getName()))destination=c;}
        if(destination==null){send(p,"tp-invalid-number");return;}plugin.getTeleportManager().startTeleport(p,destination);
    }
    private void info(Player p) {
        if(!permission(p,"dyclaim.info"))return;ClaimData c=plugin.getClaimManager().getClaimAt(p.getLocation().getChunk());if(c==null){display(p,"info-not-claimed");return;}
        p.sendMessage(plugin.getMessageManager().getMessage(p,"info-header",Map.of("{prefix}",plugin.getMessageManager().getPrefix())));
        display(p,"info-owner",Map.of("{owner}",c.getOwnerName()));
        display(p,"info-chunk",Map.of("{chunk}",c.getChunkDisplay()));
        display(p,"info-world",Map.of("{world}",c.getWorld()));
        display(p,"info-date",Map.of("{date}",displayDate(c.getClaimedAt())));
        if(c.getName()!=null)display(p,"info-name",Map.of("{name}",c.getName()));
        if(c.getCoowner()!=null)display(p,"claim-coowner",Map.of("{player}",Objects.toString(Bukkit.getOfflinePlayer(c.getCoowner()).getName(),c.getCoowner().toString())));
        for(String key:List.of("pvp","explosions","mob-explosions","mob-spawning","villager-player-damage","visitor-doors","visitor-trapdoors")) {
            var value=plugin.getAccessManager().policy(c,key);
            String status=plugin.getMessageManager().getMessage(p,value.enabled()?"state-enabled":"state-disabled");
            String legacy=switch(key){case "pvp"->"info-pvp";case "explosions"->"info-explosion";case "mob-spawning"->"info-mob";default->null;};
            if(legacy!=null)display(p,legacy,Map.of("{status}",status));
            else display(p,"info-setting",Map.of("{setting}",plugin.getMessageManager().getMessage(p,"setting-"+key),"{status}",status));
            if(value.locked())display(p,"info-setting-locked",Map.of("{setting}",plugin.getMessageManager().getMessage(p,"setting-"+key)));
        }
        if(c.getMarketPrice()!=null)display(p,"claim-market",Map.of("{price}",plugin.getEconomyManager().formatMoney(c.getMarketPrice())));
        if(c.canManage(p.getUniqueId())||p.hasPermission("dyclaim.admin")){
            display(p,"info-trusted",Map.of("{trusted}",String.valueOf(c.getTrustGrants().values().stream().filter(grant->grant.expiresAt==0||grant.expiresAt>now()).count())));
            if(c.hasSpawn())display(p,"info-custom-spawn");
        }
        p.sendMessage(plugin.getMessageManager().getMessage(p,"info-footer"));
    }
    private String displayDate(long timestamp){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp));}
    private void list(Player p,String[] args) {
        if(!permission(p,"dyclaim.list"))return;List<ClaimData> claims=plugin.getClaimManager().getPlayerClaims(p.getUniqueId());int page=args.length>=2?Integer.parseInt(args[1]):1;
        if(page<1||page>Math.max(1,(claims.size()+9)/10))throw new IllegalArgumentException("page");
        if(claims.isEmpty()){display(p,"list-empty");return;}
        p.sendMessage(plugin.getMessageManager().getMessage(p,"list-header",Map.of("{prefix}",plugin.getMessageManager().getPrefix())));
        if(claims.size()>10)display(p,"list-page",Map.of("{page}",String.valueOf(page),"{pages}",String.valueOf((claims.size()+9)/10)));
        for(int i=(page-1)*10;i<Math.min(page*10,claims.size());i++){
            ClaimData c=claims.get(i);
            Map<String,String> row=new HashMap<>(Map.of("{number}",String.valueOf(i+1),"{world}",c.getWorld(),"{x}",String.valueOf(c.getChunkX()),"{z}",String.valueOf(c.getChunkZ()),"{date}",displayDate(c.getClaimedAt()),"{chunk}",c.getChunkKey()));
            if(c.getName()!=null){row.put("{name}",c.getName());display(p,"list-named-entry",row);}else display(p,"list-entry",row);
        }
        display(p,"list-footer",Map.of("{count}",String.valueOf(claims.size()),"{max}",String.valueOf(plugin.getConfigManager().getMaxClaimsPerPlayer())));
    }
    private void language(Player p,String[] args){if(args.length!=2||!Set.of("auto","en","tr").contains(args[1]))throw new IllegalArgumentException("language");if(args[1].equals("auto"))state.languages.remove(p.getUniqueId());else state.languages.put(p.getUniqueId(),args[1]);save();send(p,"operation-success");}
    private boolean admin(Player p,String[] args) {
        if(args.length<2)return false;
        String sub=args[1].toLowerCase(Locale.ROOT);
        if(!Set.of("bypass","koruma-atla","ban","yasakla","unban","yasakkaldir","purge","temizle","transactions","islemler","give","ver","delete","sil","bulksell","toplusat").contains(sub))return false;
        if(!permission(p,"dyclaim.admin"))return true;
        switch(sub) {
            case "bypass","koruma-atla" -> {if(!permission(p,"dyclaim.admin.bypass"))return true;boolean value=args.length<3?!plugin.getAccessManager().bypass(p):parseToggle(args[2]);plugin.getAccessManager().setBypass(p,value);send(p,value?"bypass-enabled":"bypass-disabled");}
            case "ban","yasakla" -> {if(!enabled("admin.claim-bans.enabled")){send(p,"feature-disabled");return true;}if(args.length<4){send(p,"ban-usage");return true;}UUID target=known(args[2]);if(target==null){send(p,"admin-player-not-found");return true;}state.bans.put(target,duration(args[3],true));save();send(p,"operation-success");}
            case "unban","yasakkaldir" -> {if(args.length!=3){send(p,"ban-usage");return true;}UUID target=known(args[2]);if(target==null){send(p,"admin-player-not-found");return true;}state.bans.remove(target);save();send(p,"operation-success");}
            case "transactions","islemler" -> {
                var entries=plugin.getTransactionManager().unresolved();
                if(entries.isEmpty())send(p,"transactions-empty");
                else {send(p,"transactions-header");for(var entry:entries)send(p,"transactions-entry",Map.of("{id}",entry.id.toString(),"{chunk}",entry.chunk,"{stage}",entry.stage.toString()));}
            }
            case "give","ver" -> adminGive(p,args);
            case "delete","sil","bulksell","toplusat" -> adminDelete(p,args,sub.equals("bulksell")||sub.equals("toplusat"));
            case "purge","temizle" -> {
                if(!permission(p,"dyclaim.admin.purge"))return true;
                if(args.length!=3){send(p,"purge-usage");return true;}
                int days=Integer.parseInt(args[2]);if(days<=0||days>100000)throw new IllegalArgumentException("days");
                List<ClaimData> candidates=purgeCandidates(days);send(p,"purge-preview",Map.of("{count}",String.valueOf(candidates.size()),"{players}",String.valueOf(candidates.stream().map(ClaimData::getOwnerUUID).distinct().count())));
                plugin.getConfirmationManager().addPending(p.getUniqueId(),ConfirmationManager.ActionType.PURGE,uuid->{if(!p.hasPermission("dyclaim.admin")||!p.hasPermission("dyclaim.admin.purge")){send(p,"no-permission");return;}purge(candidates,days,p);},uuid->send(p,"confirm-deny"));
            }
        }
        return true;
    }
    private void adminGive(Player p,String[] args) {
        if(args.length!=3){send(p,"admin-usage-give");return;}Player target=Bukkit.getPlayerExact(args[2]);if(target==null){send(p,"admin-player-not-found");return;}
        Chunk chunk=p.getLocation().getChunk();ClaimData old=plugin.getClaimManager().getClaimAt(chunk);
        String reject=plugin.getAcquisitionRules().check(target,chunk,old,false);if(reject!=null){send(p,reject);return;}
        if(old==null)plugin.getClaimManager().giveChunk(target,chunk);else plugin.getClaimManager().transfer(old,target);
        plugin.getClaimManager().saveAllSync();send(p,"operation-success");
    }
    private void adminDelete(Player p,String[] args,boolean refund) {
        List<ClaimData> selected;
        if(refund&&args.length==2)selected=new ArrayList<>(plugin.getClaimManager().getAllClaims().values());
        else if(args.length>=4&&Set.of("all","tumu","tümü").contains(args[3])) {
            UUID target=known(args[2]);if(target==null){send(p,"admin-player-not-found");return;}
            selected=plugin.getClaimManager().getPlayerClaims(target).stream().filter(c->c.getOwnerUUID().equals(target)).toList();
        }else if(refund&&args.length==3){UUID target=known(args[2]);if(target==null){send(p,"admin-player-not-found");return;}selected=plugin.getClaimManager().getPlayerClaims(target).stream().filter(c->c.getOwnerUUID().equals(target)).toList();}
        else {
            if(args.length<3){send(p,"admin-usage-delete");return;}ClaimData c=plugin.getClaimManager().getClaimAt(p.getLocation().getChunk());
            UUID target=known(args[2]);if(c==null||target==null||!c.getOwnerUUID().equals(target)){send(p,"sell-not-owner");return;}selected=List.of(c);
        }
        Map<UUID,Long> versions=new HashMap<>();Map<UUID,Double> refunds=new HashMap<>();for(ClaimData c:selected){versions.put(c.getId(),c.getRevision());refunds.put(c.getId(),plugin.getClaimManager().refund(c));}
        send(p,"purge-preview",Map.of("{count}",String.valueOf(selected.size()),"{players}",String.valueOf(selected.stream().map(ClaimData::getOwnerUUID).distinct().count())));
        plugin.getConfirmationManager().addPending(p.getUniqueId(),ConfirmationManager.ActionType.ADMIN,uuid->{
            if(!p.hasPermission("dyclaim.admin")){send(p,"no-permission");return;}
            for(ClaimData c:selected)if(plugin.getClaimManager().getByKey(c.getChunkKey())!=c||c.getRevision()!=versions.get(c.getId())||Double.compare(refunds.get(c.getId()),plugin.getClaimManager().refund(c))!=0){send(p,"operation-changed");return;}
            plugin.getClaimManager().backup("admin-delete");Iterator<ClaimData> iterator=selected.iterator();
            new org.bukkit.scheduler.BukkitRunnable(){public void run(){if(!p.hasPermission("dyclaim.admin")){cancel();return;}int n=0;while(iterator.hasNext()&&n++<plugin.getConfigManager().values().getInt("admin.purge.batch-size")){ClaimData c=iterator.next();if(plugin.getClaimManager().getByKey(c.getChunkKey())==c&&c.getRevision()==versions.get(c.getId())&&!plugin.getTransactionManager().isLocked(c.getChunkKey())){if(refund)plugin.getClaimManager().refundAndRemove(c);else plugin.getClaimManager().remove(c.getChunkKey());}}plugin.getClaimManager().saveAllSync();if(!iterator.hasNext()){send(p,"operation-success");cancel();}}}.runTaskTimer(plugin,1,1);
        },uuid->send(p,"confirm-deny"));
    }
    private void recordSeen(Player p){Seen seen=new Seen();seen.at=now();seen.exempt=p.hasPermission("dyclaim.admin")||p.hasPermission("dyclaim.admin.purge.exempt");state.lastSeen.put(p.getUniqueId(),seen);}
    private boolean inactive(UUID uuid,int days) {
        if(Bukkit.getPlayer(uuid)!=null)return false;
        Seen seen=state.lastSeen.get(uuid);
        return seen!=null&&!seen.exempt&&seen.at>0&&now()-seen.at>=days*86_400_000L;
    }
    private boolean purgeEligible(ClaimData c,int days){return inactive(c.getOwnerUUID(),days)&&(c.getCoowner()==null||!enabled("admin.purge.protect-active-coowners")||inactive(c.getCoowner(),days))&&!plugin.getTransactionManager().isLocked(c.getChunkKey());}
    private List<ClaimData> purgeCandidates(int days){return plugin.getClaimManager().getAllClaims().values().stream().filter(c->purgeEligible(c,days)).toList();}
    private void purge(List<ClaimData> candidates,int days,Player requester) {
        if(candidates.isEmpty()){if(requester!=null)send(requester,"operation-success");return;}
        plugin.getClaimManager().backup("purge");Iterator<ClaimData> iterator=candidates.iterator();
        new org.bukkit.scheduler.BukkitRunnable(){int removed=0;public void run(){
            if((requester==null&&!enabled("admin.purge.scheduled-enabled"))||(requester!=null&&(!requester.hasPermission("dyclaim.admin")||!requester.hasPermission("dyclaim.admin.purge")))){cancel();return;}
            int batch=plugin.getConfigManager().values().getInt("admin.purge.batch-size");int count=0;
            while(iterator.hasNext()&&count++<batch){ClaimData c=iterator.next();if(plugin.getClaimManager().getByKey(c.getChunkKey())==c&&purgeEligible(c,days)){
                UUID id=c.getId();boolean result=enabled("admin.purge.refund")?plugin.getClaimManager().refundAndRemove(c):plugin.getClaimManager().remove(c.getChunkKey());
                if(result){removed++;plugin.getLogger().info("Purge removed claim "+id+" "+c.getChunkKey());}
            }}
            plugin.getClaimManager().saveAllSync();if(!iterator.hasNext()){if(requester!=null)send(requester,"purge-complete",Map.of("{count}",String.valueOf(removed)));cancel();}
        }}.runTaskTimer(plugin,1,1);
    }
    private String warningKey(ClaimData claim,UUID uuid){return claim.getId()+":"+uuid;}
    private boolean eligibleWarning(Player target,ClaimData c) {
        return !c.canManage(target.getUniqueId())&&!c.isTrusted(target.getUniqueId())&&!plugin.getClanTrustManager().allows(target.getUniqueId(),c,"build")
                &&!target.hasPermission("dyclaim.admin")&&!target.hasPermission("dyclaim.admin.bypass")&&!target.hasPermission("dyclaim.warn.exempt");
    }
    private void warn(Player p,String[] args) {
        if(!permission(p,"dyclaim.warn")||!enabled("warnings.enabled")){send(p,"feature-disabled");return;}ClaimData c=owned(p,false);if(c==null)return;
        if(args.length!=2){send(p,"warn-usage");return;}Player target=Bukkit.getPlayerExact(args[1]);
        if(target==null||plugin.getClaimManager().getClaimAt(target.getLocation().getChunk())!=c||!eligibleWarning(target,c)){send(p,"warn-ineligible");return;}
        String key=warningKey(c,target.getUniqueId());WarningChain chain=state.warnings.computeIfAbsent(key,k->new WarningChain());
        if(!chain.stage.equals("pending")){send(p,"warn-already-applied");return;}
        long now=now(),cooldown=plugin.getConfigManager().values().getLong("warnings.cooldown-seconds")*1000,window=plugin.getConfigManager().values().getLong("warnings.window-seconds")*1000;
        if(WarningRules.record(chain.times,now,cooldown,window)==0){send(p,"warn-cooldown");return;}
        int needed=plugin.getConfigManager().values().getInt("warnings.required-count");
        chain.target=target.getUniqueId();chain.name=target.getName();
        send(target,"warning-received",Map.of("{count}",String.valueOf(chain.times.size()),"{max}",String.valueOf(needed),"{type}",plugin.getMessageManager().getMessage(target,"punishment-"+plugin.getConfigManager().values().getString("warnings.punishment.type")),"{duration}",plugin.getConfigManager().values().getString("warnings.punishment.duration")));
        send(p,"operation-success");if(enabled("warnings.sound-enabled"))target.playSound(target.getLocation(),Sound.valueOf(plugin.getConfigManager().values().getString("warnings.sound")),(float)plugin.getConfigManager().values().getDouble("warnings.sound-volume"),(float)plugin.getConfigManager().values().getDouble("warnings.sound-pitch"));
        if(chain.times.size()>=needed) {
            chain.mode=plugin.getConfigManager().values().getString("warnings.punishment.type");
            chain.until=Rules.expiry(plugin.getConfigManager().values().getString("warnings.punishment.duration"),now,Long.MAX_VALUE,false);
            // Persist before the external ban. An interrupted application is never silently retried.
            chain.stage="applying";save();
            if(chain.mode.equals("server-ban")) {
                org.bukkit.BanList<org.bukkit.profile.PlayerProfile> bans=Bukkit.getBanList(BanList.Type.PROFILE);
                bans.addBan(target.getPlayerProfile(),plugin.getMessageManager().getMessage(target,"warning-ban-reason"),new Date(chain.until),"DyClaim");
                chain.stage="applied";save();target.kickPlayer(plugin.getMessageManager().getMessage(target,"warning-ban-reason"));
            }else{chain.stage="applied";save();eject(target,c);}
        }else save();
    }
    private boolean entryBanned(Player p,ClaimData c){WarningChain chain=state.warnings.get(warningKey(c,p.getUniqueId()));return enabled("warnings.enabled")&&chain!=null&&"applied".equals(chain.stage)&&"claim-entry-ban".equals(chain.mode)&&chain.until>now()&&eligibleWarning(p,c);}
    private void eject(Player p,ClaimData c) {
        World world=p.getWorld();
        for(int radius=1;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.abs(dx)!=radius&&Math.abs(dz)!=radius)continue;int x=c.getChunkX()+dx,z=c.getChunkZ()+dz;
            if(!world.isChunkLoaded(x,z)||plugin.getClaimManager().isChunkClaimed(world.getName(),x,z))continue;
            Location loc=new Location(world,x*16+8.5,world.getHighestBlockYAt(x*16+8,z*16+8)+1,z*16+8.5);
            if(TeleportManager.safe(loc)){p.teleport(loc);return;}
        }
        send(p,"entry-exit-unavailable");
    }
    @EventHandler public void join(PlayerJoinEvent event){recordSeen(event.getPlayer());save();ClaimData c=plugin.getClaimManager().getClaimAt(event.getPlayer().getLocation().getChunk());if(c!=null&&entryBanned(event.getPlayer(),c))eject(event.getPlayer(),c);}
    @EventHandler public void quit(PlayerQuitEvent event){Player p=event.getPlayer();recordSeen(p);auto.remove(p.getUniqueId());autoAttempt.remove(p.getUniqueId());plugin.getConfirmationManager().cancelPending(p.getUniqueId());plugin.getAccessManager().quit(p.getUniqueId());plugin.getChunkVisualizer().cleanupPlayer(p);save();}
    @EventHandler public void world(PlayerChangedWorldEvent event){auto.remove(event.getPlayer().getUniqueId());plugin.getChunkVisualizer().cleanupPlayer(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void entry(PlayerMoveEvent event){
        if(event.getTo()==null)return;Player p=event.getPlayer();ClaimData to=plugin.getClaimManager().getClaimAt(event.getTo().getChunk());
        if(to!=null&&entryBanned(p,to)){ClaimData from=plugin.getClaimManager().getClaimAt(event.getFrom().getChunk());if(from!=to)event.setCancelled(true);else Bukkit.getScheduler().runTask(plugin,()->eject(p,to));}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event){
        if(event.getTo()==null)return;
        if(event.getFrom().getWorld()==event.getTo().getWorld()&&(event.getFrom().getBlockX()>>4)==(event.getTo().getBlockX()>>4)&&(event.getFrom().getBlockZ()>>4)==(event.getTo().getBlockZ()>>4))return;
        Player p=event.getPlayer();ClaimData from=plugin.getClaimManager().getClaimAt(event.getFrom().getChunk()),to=plugin.getClaimManager().getClaimAt(event.getTo().getChunk());
        if(enabled("warnings.reset-on-exit")&&from!=null&&from!=to){String key=warningKey(from,p.getUniqueId());WarningChain chain=state.warnings.get(key);if(chain!=null&&chain.stage.equals("pending")){state.warnings.remove(key);save();}}
        if(!auto.contains(p.getUniqueId())||event instanceof PlayerTeleportEvent||p.isInsideVehicle()||to!=null)return;
        if(!enabled("auto-claim.enabled")||!p.hasPermission("dyclaim.auto")||!p.hasPermission("dyclaim.claim")){auto.remove(p.getUniqueId());send(p,"auto-disabled");return;}
        long last=autoAttempt.getOrDefault(p.getUniqueId(),0L);if(now()-last<1000)return;autoAttempt.put(p.getUniqueId(),now());
        Chunk chunk=event.getTo().getChunk();double price=plugin.getEconomyManager().isEnabled()?plugin.getAcquisitionRules().price(chunk.getWorld().getName()):0;
        if((enabled("auto-claim.require-economy")&&!plugin.getEconomyManager().isEnabled())||(!enabled("auto-claim.allow-zero-price")&&price==0)){auto.remove(p.getUniqueId());send(p,"auto-disabled");return;}
        String rejection=plugin.getAcquisitionRules().check(p,chunk,null,false);
        if(rejection!=null){if(Set.of("acquire-limit","claim-banned","claiming-disabled").contains(rejection)){auto.remove(p.getUniqueId());send(p,rejection);send(p,"auto-disabled");}return;}
        if(plugin.getCooldownManager().hasCooldown(p.getUniqueId()))return;
        if(!acquire(p,chunk,price,true)){auto.remove(p.getUniqueId());send(p,"auto-disabled");}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void teleportEntry(PlayerTeleportEvent event){entry(event);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleportExit(PlayerTeleportEvent event){move(event);}
    public void reloadTasks(){
        if(mobTask!=null)mobTask.cancel();if(purgeTask!=null)purgeTask.cancel();mobTask=null;purgeTask=null;
        mobClaims=Collections.emptyIterator();mobEntities=Collections.emptyIterator();auto.clear();
        if(enabled("protection.mobs.remove-on-entry.enabled"))mobTask=Bukkit.getScheduler().runTaskTimer(plugin,this::scanMobs,20,plugin.getConfigManager().values().getLong("protection.mobs.remove-on-entry.interval-ticks"));
        if(enabled("admin.purge.scheduled-enabled")){
            long interval=plugin.getConfigManager().values().getLong("admin.purge.check-interval-minutes")*1200;
            purgeTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{int days=plugin.getConfigManager().values().getInt("admin.purge.inactive-days");purge(purgeCandidates(days),days,null);},interval,interval);
        }
    }
    private void scanMobs(){
        var cfg=plugin.getConfigManager().values();int budget=cfg.getInt("protection.mobs.remove-on-entry.batch-size");
        for(int processed=0;processed<budget;processed++) {
            if(mobEntities.hasNext()) {
                Entity e=mobEntities.next();if(e.isValid()&&e instanceof Mob&&plugin.getClaimManager().getClaimAt(e.getLocation().getChunk())==scanningClaim&&removeMob(e))e.remove();continue;
            }
            if(!mobClaims.hasNext()){mobClaims=new ArrayList<>(plugin.getClaimManager().getAllClaims().keySet()).iterator();return;}
            ClaimData c=plugin.getClaimManager().getByKey(mobClaims.next());if(c==null)continue;
            if(cfg.getBoolean("protection.mobs.remove-on-entry.only-when-spawning-disabled")&&plugin.getAccessManager().enabled(c,"mob-spawning"))continue;
            World w=Bukkit.getWorld(c.getWorld());if(w==null||!w.isChunkLoaded(c.getChunkX(),c.getChunkZ()))continue;
            scanningClaim=c;mobEntities=Arrays.asList(w.getChunkAt(c.getChunkX(),c.getChunkZ()).getEntities()).iterator();
        }
    }
    private boolean removeMob(Entity entity){
        var cfg=plugin.getConfigManager().values();String prefix="protection.mobs.remove-on-entry.";
        if(cfg.getBoolean(prefix+"protect-named")&&entity.getCustomName()!=null)return false;
        if(cfg.getBoolean(prefix+"protect-tamed")&&entity instanceof Tameable tame&&tame.isTamed())return false;
        if(cfg.getStringList(prefix+"excluded-types").contains(entity.getType().name()))return false;
        return switch(cfg.getString(prefix+"entity-scope")){case "all-mobs"->true;case "list"->cfg.getStringList(prefix+"included-types").contains(entity.getType().name());default->entity instanceof Monster;};
    }
    public void close(){if(mobTask!=null)mobTask.cancel();if(purgeTask!=null)purgeTask.cancel();for(Player p:Bukkit.getOnlinePlayers())recordSeen(p);save();}
}


