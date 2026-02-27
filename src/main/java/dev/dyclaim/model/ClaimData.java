package dev.dyclaim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        return getTrustedPlayers().contains(playerUUID);
    }

    public boolean addTrusted(UUID playerUUID) {
        if (isTrusted(playerUUID))
            return false;
        getTrustedPlayers().add(playerUUID);
        return true;
    }

    public boolean removeTrusted(UUID playerUUID) {
        return getTrustedPlayers().remove(playerUUID);
    }

    public boolean isAllowed(UUID playerUUID) {
        return ownerUUID.equals(playerUUID) || isTrusted(playerUUID);
    }

    public String getChunkKey() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public String getChunkDisplay() {
        return chunkX + ", " + chunkZ;
    }
}
