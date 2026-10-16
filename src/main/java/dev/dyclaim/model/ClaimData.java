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

    public ClaimData() {
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

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
    }

    public boolean isPvpDisabled() {
        return pvpDisabled == null || pvpDisabled;
    }

    public void setPvpDisabled(boolean pvpDisabled) {
        this.pvpDisabled = pvpDisabled;
    }

    public boolean isExplosionDisabled() {
        return explosionDisabled == null || explosionDisabled;
    }

    public void setExplosionDisabled(boolean explosionDisabled) {
        this.explosionDisabled = explosionDisabled;
    }

    public boolean isMobSpawnDisabled() {
        return mobSpawnDisabled == null || mobSpawnDisabled;
    }

    public void setMobSpawnDisabled(boolean mobSpawnDisabled) {
        this.mobSpawnDisabled = mobSpawnDisabled;
    }

    public List<UUID> getTrustedPlayers() {
        if (trustedPlayers == null)
            trustedPlayers = new ArrayList<>();
        return trustedPlayers;
    }

    public boolean isTrusted(UUID playerUUID) {
        TrustGrant grant = getTrustGrants().get(playerUUID);
        return grant != null && (grant.expiresAt == 0 || grant.expiresAt > System.currentTimeMillis());
    }

    public boolean addTrusted(UUID playerUUID) {
        if (isTrusted(playerUUID))
            return false;
        getTrustedPlayers().add(playerUUID);
        getTrustGrants().put(playerUUID, new TrustGrant(0));
        return true;
    }

    public boolean removeTrusted(UUID playerUUID) {
        boolean removed = getTrustGrants().remove(playerUUID) != null;
        getTrustedPlayers().remove(playerUUID);
        return removed;
    }

    public boolean isAllowed(UUID playerUUID) {
        return canManage(playerUUID) || allows(playerUUID, "build");
    }

    public UUID getId() { if (id == null) id = UUID.randomUUID(); return id; }
    public UUID getCoowner() { return coowner; }
    public void setCoowner(UUID uuid) { coowner = uuid; touch(); }
    public boolean canManage(UUID uuid) { return ownerUUID.equals(uuid) || uuid.equals(coowner); }
    public boolean allows(UUID uuid, String right) {
        if (canManage(uuid)) return true;
        TrustGrant grant = getTrustGrants().get(uuid);
        return grant != null && grant.allows(right, System.currentTimeMillis());
    }
    public Map<UUID, TrustGrant> getTrustGrants() {
        if (trustGrants == null) {
            trustGrants = new HashMap<>();
            for (UUID uuid : getTrustedPlayers()) trustGrants.put(uuid, new TrustGrant(0));
        }
        return trustGrants;
    }
    public Map<String, TrustGrant> getClanGrants() { if (clanGrants == null) clanGrants = new HashMap<>(); return clanGrants; }
    public void grant(UUID uuid, long expiry) { getTrustedPlayers().remove(uuid); getTrustGrants().put(uuid, new TrustGrant(expiry)); touch(); }
    public Map<String, Boolean> getSettings() { if (settings == null) settings = new HashMap<>(); return settings; }
    public Boolean getChoice(String key) {
        return switch (key) { case "pvp" -> pvpDisabled == null ? null : !pvpDisabled; case "explosions" -> explosionDisabled == null ? null : !explosionDisabled;
            case "mob-spawning" -> mobSpawnDisabled == null ? null : !mobSpawnDisabled; default -> getSettings().get(key); };
    }
    public void setChoice(String key, boolean value) {
        switch (key) { case "pvp" -> pvpDisabled = !value; case "explosions" -> explosionDisabled = !value; case "mob-spawning" -> mobSpawnDisabled = !value; default -> getSettings().put(key, value); }
        touch();
    }
    public void inheritChoices() { pvpDisabled = null; explosionDisabled = null; mobSpawnDisabled = null; }
    public String getName() { return name; }
    public void setName(String value) { name = value; touch(); }
    public long getRevision() { return revision; }
    public void touch() { revision++; }
    public Double getMarketPrice() { return marketPrice; }
    public void setMarketPrice(Double value) { marketPrice = value; touch(); }
    public void clearAccess() { trustedPlayers = new ArrayList<>(); trustGrants = new HashMap<>(); clanGrants = new HashMap<>(); coowner = null; marketPrice = null; touch(); }
    public Double getPaidPrice() { return paidPrice; }
    public double getRefundBasis(double fallback) { return refundBasis == null ? fallback : refundBasis; }
    public void recordPrice(double price) { paidPrice = price; refundBasis = price; }
    public void setRefundBasis(double price) { refundBasis = price; touch(); }
    public void migrate(double legacyPrice) {
        getId(); getTrustGrants(); getSettings(); getClanGrants();
        if (refundBasis == null) refundBasis = legacyPrice;
    }
    public boolean hasSpawn() { return spawnX != null && spawnY != null && spawnZ != null; }
    public void setSpawn(double x, double y, double z, float yaw, float pitch) { spawnX=x; spawnY=y; spawnZ=z; spawnYaw=yaw; spawnPitch=pitch; touch(); }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnYaw() { return spawnYaw == null ? 0 : spawnYaw; }
    public float getSpawnPitch() { return spawnPitch == null ? 0 : spawnPitch; }

    public String getChunkKey() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public String getChunkDisplay() {
        return chunkX + ", " + chunkZ;
    }
}
