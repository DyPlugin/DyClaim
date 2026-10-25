package dev.dyclaim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class ClaimData {

    private UUID ownerUUID;
    private String ownerName;
    private String world;
    private int chunkX;
    private int chunkZ;
    private long claimedAt;

    private Boolean pvpDisabled;
    private Boolean explosionDisabled;
    private Boolean mobSpawnDisabled;
    private List<UUID> trustedPlayers;
    private UUID id;
    private UUID coowner;
    private String name;
    private Map<UUID, TrustGrant> trustGrants;
    private Map<String, Boolean> settings;
    private Map<String, TrustGrant> clanGrants;
    private Double paidPrice;
    private Double refundBasis;
    private Double marketPrice;
    private long revision;
    private Double spawnX, spawnY, spawnZ;
    private Float spawnYaw, spawnPitch;
    private String greeting;
    private Map<UUID, Long> visitorBans;

    public ClaimData() {
    }
    public synchronized void transferOwnership(UUID owner,String ownerName){setOwnerUUID(owner);setOwnerName(ownerName);clearAccess();}

    /** Detached record used for persistence; live mutable collections never reach the writer. */
    public synchronized ClaimData snapshot() {
        ClaimData copy=new ClaimData();
        copy.ownerUUID=ownerUUID;copy.ownerName=ownerName;copy.world=world;copy.chunkX=chunkX;copy.chunkZ=chunkZ;copy.claimedAt=claimedAt;
        copy.pvpDisabled=pvpDisabled;copy.explosionDisabled=explosionDisabled;copy.mobSpawnDisabled=mobSpawnDisabled;
        copy.trustedPlayers=trustedPlayers==null?null:new ArrayList<>(trustedPlayers);
        copy.id=id;copy.coowner=coowner;copy.name=name;copy.trustGrants=copyGrants(trustGrants);copy.clanGrants=copyGrants(clanGrants);
        copy.settings=settings==null?null:new HashMap<>(settings);copy.paidPrice=paidPrice;copy.refundBasis=refundBasis;copy.marketPrice=marketPrice;copy.revision=revision;
        copy.spawnX=spawnX;copy.spawnY=spawnY;copy.spawnZ=spawnZ;copy.spawnYaw=spawnYaw;copy.spawnPitch=spawnPitch;
        copy.greeting=greeting;copy.visitorBans=visitorBans==null?null:new HashMap<>(visitorBans);
        return copy;
    }
    private static <K> Map<K,TrustGrant> copyGrants(Map<K,TrustGrant> source){
        if(source==null)return null;
        Map<K,TrustGrant> copy=new HashMap<>();source.forEach((key,grant)->{
            if(grant==null){copy.put(key,null);return;}
            TrustGrant value=new TrustGrant();value.expiresAt=grant.expiresAt;
            value.permissions=grant.permissions==null?null:new java.util.HashSet<>(grant.permissions);copy.put(key,value);
        });return copy;
    }
    public synchronized void grantClan(String clan,long expiry){getClanGrants().put(clan,new TrustGrant(expiry));touch();}
    public synchronized boolean removeClan(String clan){return getClanGrants().remove(clan)!=null;}
    public synchronized boolean changeTrustPermission(UUID uuid,String right,boolean allow){
        TrustGrant grant=getTrustGrants().get(uuid);
        if(grant==null||!grant.allowsExpiry(System.currentTimeMillis()))return false;
        if(!TrustGrant.RIGHTS.contains(right))throw new IllegalArgumentException("Unknown trust permission");
        if(allow)grant.permissions.add(right);else grant.permissions.remove(right);touch();return true;
    }

    public ClaimData(UUID ownerUUID, String ownerName, String world, int chunkX, int chunkZ) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = System.currentTimeMillis();
        this.pvpDisabled = true;
        this.explosionDisabled = true;
        this.mobSpawnDisabled = true;
        this.trustedPlayers = new ArrayList<>();
        this.id = UUID.randomUUID();
    }

    public synchronized UUID getOwnerUUID() {
        return ownerUUID;
    }

    public synchronized void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public synchronized String getOwnerName() {
        return ownerName;
    }

    public synchronized void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public synchronized String getWorld() {
        return world;
    }

    public synchronized void setWorld(String world) {
        this.world = world;
    }

    public synchronized int getChunkX() {
        return chunkX;
    }

    public synchronized void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public synchronized int getChunkZ() {
        return chunkZ;
    }

    public synchronized void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    public synchronized long getClaimedAt() {
        return claimedAt;
    }

    public synchronized void setClaimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
    }

    public synchronized boolean isPvpDisabled() {
        return pvpDisabled == null || pvpDisabled;
    }

    public synchronized void setPvpDisabled(boolean pvpDisabled) {
        this.pvpDisabled = pvpDisabled;
    }

    public synchronized boolean isExplosionDisabled() {
        return explosionDisabled == null || explosionDisabled;
    }

    public synchronized void setExplosionDisabled(boolean explosionDisabled) {
        this.explosionDisabled = explosionDisabled;
    }

    public synchronized boolean isMobSpawnDisabled() {
        return mobSpawnDisabled == null || mobSpawnDisabled;
    }

    public synchronized void setMobSpawnDisabled(boolean mobSpawnDisabled) {
        this.mobSpawnDisabled = mobSpawnDisabled;
    }

    public synchronized List<UUID> getTrustedPlayers() {
        if (trustedPlayers == null)
            trustedPlayers = new ArrayList<>();
        return trustedPlayers;
    }

    public synchronized boolean isTrusted(UUID playerUUID) {
        TrustGrant grant = getTrustGrants().get(playerUUID);
        return grant != null && (grant.expiresAt == 0 || grant.expiresAt > System.currentTimeMillis());
    }

    public synchronized boolean addTrusted(UUID playerUUID) {
        if (isTrusted(playerUUID))
            return false;
        getTrustedPlayers().add(playerUUID);
        getTrustGrants().put(playerUUID, new TrustGrant(0));
        return true;
    }

    public synchronized boolean removeTrusted(UUID playerUUID) {
        boolean removed = getTrustGrants().remove(playerUUID) != null;
        getTrustedPlayers().remove(playerUUID);
        return removed;
    }

    public synchronized boolean isAllowed(UUID playerUUID) {
        return canManage(playerUUID) || allows(playerUUID, "build");
    }

    public synchronized UUID getId() { if (id == null) id = UUID.randomUUID(); return id; }
    public synchronized UUID getCoowner() { return coowner; }
    public synchronized void setCoowner(UUID uuid) { coowner = uuid; touch(); }
    public synchronized boolean canManage(UUID uuid) { return ownerUUID.equals(uuid) || uuid.equals(coowner); }
    public synchronized boolean allows(UUID uuid, String right) {
        if (canManage(uuid)) return true;
        TrustGrant grant = getTrustGrants().get(uuid);
        return grant != null && grant.allows(right, System.currentTimeMillis());
    }
    public synchronized Map<UUID, TrustGrant> getTrustGrants() {
        if (trustGrants == null) {
            trustGrants = new HashMap<>();
            for (UUID uuid : getTrustedPlayers()) trustGrants.put(uuid, new TrustGrant(0));
        }
        return trustGrants;
    }
    public synchronized Map<String, TrustGrant> getClanGrants() { if (clanGrants == null) clanGrants = new HashMap<>(); return clanGrants; }
    public synchronized void grant(UUID uuid, long expiry) { getTrustedPlayers().remove(uuid); getTrustGrants().put(uuid, new TrustGrant(expiry)); touch(); }
    public synchronized Map<String, Boolean> getSettings() { if (settings == null) settings = new HashMap<>(); return settings; }
    public synchronized Boolean getChoice(String key) {
        return switch (key) { case "pvp" -> pvpDisabled == null ? null : !pvpDisabled; case "explosions" -> explosionDisabled == null ? null : !explosionDisabled;
            case "mob-spawning" -> mobSpawnDisabled == null ? null : !mobSpawnDisabled; default -> getSettings().get(key); };
    }
    public synchronized void setChoice(String key, boolean value) {
        switch (key) { case "pvp" -> pvpDisabled = !value; case "explosions" -> explosionDisabled = !value; case "mob-spawning" -> mobSpawnDisabled = !value; default -> getSettings().put(key, value); }
        touch();
    }
    public synchronized void inheritChoices() { pvpDisabled = null; explosionDisabled = null; mobSpawnDisabled = null; }
    public synchronized String getName() { return name; }
    public synchronized void setName(String value) { name = value; touch(); }
    public synchronized long getRevision() { return revision; }
    public synchronized void touch() { revision++; }
    public synchronized Double getMarketPrice() { return marketPrice; }
    public synchronized void setMarketPrice(Double value) { marketPrice = value; touch(); }
    public synchronized void clearAccess() { trustedPlayers = new ArrayList<>(); trustGrants = new HashMap<>(); clanGrants = new HashMap<>(); coowner = null; marketPrice = null; greeting = null; visitorBans = new HashMap<>(); touch(); }
    public synchronized Double getPaidPrice() { return paidPrice; }
    public synchronized double getRefundBasis(double fallback) { return refundBasis == null ? fallback : refundBasis; }
    public synchronized void recordPrice(double price) { paidPrice = price; refundBasis = price; }
    public synchronized void setRefundBasis(double price) { refundBasis = price; touch(); }
    public synchronized void migrate(double legacyPrice) {
        getId(); getTrustGrants(); getSettings(); getClanGrants();
        if (refundBasis == null) refundBasis = legacyPrice;
    }
    public synchronized boolean hasSpawn() { return spawnX != null && spawnY != null && spawnZ != null; }
    public synchronized void setSpawn(double x, double y, double z, float yaw, float pitch) { spawnX=x; spawnY=y; spawnZ=z; spawnYaw=yaw; spawnPitch=pitch; touch(); }
    public synchronized double getSpawnX() { return spawnX; }
    public synchronized double getSpawnY() { return spawnY; }
    public synchronized double getSpawnZ() { return spawnZ; }
    public synchronized float getSpawnYaw() { return spawnYaw == null ? 0 : spawnYaw; }
    public synchronized float getSpawnPitch() { return spawnPitch == null ? 0 : spawnPitch; }

    /** Permanent trust limited to the given rights; merges with an existing grant. */
    public synchronized void grantRights(UUID uuid, Set<String> rights) {
        TrustGrant grant = getTrustGrants().get(uuid);
        if (grant == null) { grant = new TrustGrant(); getTrustGrants().put(uuid, grant); }
        for (String right : rights) if (TrustGrant.RIGHTS.contains(right)) grant.permissions.add(right);
        touch();
    }
    public synchronized String getGreeting() { return greeting; }
    public synchronized void setGreeting(String value) { greeting = value; touch(); }
    /** Visitor bans keyed by player; 0 means permanent, otherwise the expiry time in milliseconds. */
    public synchronized Map<UUID, Long> getVisitorBans() { if (visitorBans == null) visitorBans = new HashMap<>(); return visitorBans; }
    public synchronized boolean isVisitorBanned(UUID uuid) {
        Long expiry = getVisitorBans().get(uuid);
        return expiry != null && (expiry == 0 || expiry > System.currentTimeMillis());
    }
    public synchronized void banVisitor(UUID uuid, long expiry) { getVisitorBans().put(uuid, expiry); touch(); }
    public synchronized boolean unbanVisitor(UUID uuid) { boolean removed = getVisitorBans().remove(uuid) != null; if (removed) touch(); return removed; }

    public synchronized String getChunkKey() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public synchronized String getChunkDisplay() {
        return chunkX + ", " + chunkZ;
    }
}
