package dev.dyclaim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * One claimed chunk. Writers hold the monitor. The fields every protection event reads (owner, coowner, position and
 * the trust and clan grant maps) are volatile and read without the lock. The two grant maps are copy-on-write: a writer
 * publishes a new map instead of changing a published one, and a grant's rights change by publishing a new set.
 */
public class ClaimData {

    private volatile UUID ownerUUID;
    private String ownerName;
    private volatile String world;
    private volatile int chunkX;
    private volatile int chunkZ;
    private long claimedAt;

    private Boolean pvpDisabled;
    private Boolean explosionDisabled;
    private Boolean mobSpawnDisabled;
    private List<UUID> trustedPlayers;
    private UUID id;
    private volatile UUID coowner;
    private String name;
    private volatile Map<UUID, TrustGrant> trustGrants;
    private Map<String, Boolean> settings;
    private volatile Map<String, TrustGrant> clanGrants;
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
    /** Rights set to publish in place of the grant's current one; a published set is never changed. */
    private static Set<String> editableRights(TrustGrant grant){
        return grant.permissions==null?new java.util.HashSet<>():new java.util.HashSet<>(grant.permissions);
    }
    public synchronized void grantClan(String clan,long expiry){Map<String,TrustGrant> next=new HashMap<>(getClanGrants());next.put(clan,new TrustGrant(expiry));clanGrants=next;touch();}
    public synchronized boolean removeClan(String clan){
        if(!getClanGrants().containsKey(clan))return false;
        Map<String,TrustGrant> next=new HashMap<>(clanGrants);next.remove(clan);clanGrants=next;return true;
    }
    public synchronized boolean changeTrustPermission(UUID uuid,String right,boolean allow){
        TrustGrant grant=getTrustGrants().get(uuid);
        if(grant==null||!grant.allowsExpiry(System.currentTimeMillis()))return false;
        if(!TrustGrant.RIGHTS.contains(right))throw new IllegalArgumentException("Unknown trust permission");
        Set<String> rights=editableRights(grant);
        if(allow)rights.add(right);else rights.remove(right);
        grant.permissions=rights;touch();return true;
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
        // Published up front so protection reads never take the lock to create them.
        this.trustGrants = new HashMap<>();
        this.clanGrants = new HashMap<>();
        this.id = UUID.randomUUID();
    }

    public UUID getOwnerUUID() {
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

    public String getWorld() {
        return world;
    }

    public synchronized void setWorld(String world) {
        this.world = world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public synchronized void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkZ() {
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

    /** Lock-free: reads the published grant map. */
    public boolean isTrusted(UUID playerUUID) {
        TrustGrant grant = getTrustGrants().get(playerUUID);
        return grant != null && (grant.expiresAt == 0 || grant.expiresAt > System.currentTimeMillis());
    }

    public synchronized boolean addTrusted(UUID playerUUID) {
        if (isTrusted(playerUUID))
            return false;
        getTrustedPlayers().add(playerUUID);
        putGrant(playerUUID, new TrustGrant(0));
        return true;
    }

    public synchronized boolean removeTrusted(UUID playerUUID) {
        boolean removed = getTrustGrants().containsKey(playerUUID);
        if (removed) { Map<UUID, TrustGrant> next = new HashMap<>(trustGrants); next.remove(playerUUID); trustGrants = next; }
        getTrustedPlayers().remove(playerUUID);
        return removed;
    }

    /** Same answer as {@code canManage(uuid) || allows(uuid, "build")}; {@link #allows} already answers managers first. */
    public boolean isAllowed(UUID playerUUID) {
        return allows(playerUUID, "build");
    }

    public synchronized UUID getId() { if (id == null) id = UUID.randomUUID(); return id; }
    public UUID getCoowner() { return coowner; }
    public synchronized void setCoowner(UUID uuid) { coowner = uuid; touch(); }
    /** Lock-free: owner and coowner are volatile and replaced as whole values. */
    public boolean canManage(UUID uuid) { return ownerUUID.equals(uuid) || uuid.equals(coowner); }
    /** Owner of claims made with /claim admin server; no player can own, sell or transfer them. */
    public static final UUID SERVER = new UUID(0L, 0L);
    public boolean isServerClaim() { return SERVER.equals(ownerUUID); }
    /** Lock-free; the owner and coowner match answers before the grant map is read. */
    public boolean allows(UUID uuid, String right) {
        if (canManage(uuid)) return true;
        TrustGrant grant = getTrustGrants().get(uuid);
        return grant != null && grant.allows(right, System.currentTimeMillis());
    }
    /**
     * The published player grant map. It is replaced, never changed, by the writers in this class; callers only read it.
     * Claims saved before grants existed build it once from the old trusted list.
     */
    public Map<UUID, TrustGrant> getTrustGrants() {
        Map<UUID, TrustGrant> grants = trustGrants;
        return grants != null ? grants : legacyGrants();
    }
    private synchronized Map<UUID, TrustGrant> legacyGrants() {
        if (trustGrants == null) {
            Map<UUID, TrustGrant> built = new HashMap<>();
            for (UUID uuid : getTrustedPlayers()) built.put(uuid, new TrustGrant(0));
            trustGrants = built;
        }
        return trustGrants;
    }
    /** Publishes a copy of the grant map with this player's grant replaced; callers hold the monitor. */
    private void putGrant(UUID uuid, TrustGrant grant) {
        Map<UUID, TrustGrant> next = new HashMap<>(getTrustGrants()); next.put(uuid, grant); trustGrants = next;
    }
    /** The published clan and permission-group grant map; replaced, never changed, by the writers in this class. */
    public Map<String, TrustGrant> getClanGrants() {
        Map<String, TrustGrant> grants = clanGrants;
        return grants != null ? grants : emptyClanGrants();
    }
    private synchronized Map<String, TrustGrant> emptyClanGrants() { if (clanGrants == null) clanGrants = new HashMap<>(); return clanGrants; }
    /** Replaces the player's grant with exactly these rights; 0 is permanent, otherwise the expiry in epoch milliseconds. */
    public synchronized void setTrust(UUID uuid, Set<String> rights, long expiry) {
        getTrustedPlayers().remove(uuid);
        TrustGrant grant = new TrustGrant(); grant.expiresAt = expiry; grant.permissions = new java.util.HashSet<>(rights);
        putGrant(uuid, grant); touch();
    }
    /** Rights of the player's active grant, empty when there is none or it has expired. */
    public synchronized Set<String> activeTrustRights(UUID uuid) {
        TrustGrant grant = getTrustGrants().get(uuid);
        if (grant == null || grant.permissions == null || !grant.allowsExpiry(System.currentTimeMillis())) return Set.of();
        return Set.copyOf(grant.permissions);
    }
    public synchronized void grant(UUID uuid, long expiry) { getTrustedPlayers().remove(uuid); putGrant(uuid, new TrustGrant(expiry)); touch(); }
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
        if (grant == null) {
            grant = new TrustGrant();
            for (String right : rights) if (TrustGrant.RIGHTS.contains(right)) grant.permissions.add(right);
            putGrant(uuid, grant);
        } else {
            Set<String> merged = editableRights(grant);
            for (String right : rights) if (TrustGrant.RIGHTS.contains(right)) merged.add(right);
            grant.permissions = merged;
        }
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
