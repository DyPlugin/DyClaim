package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final DyClaim plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(DyClaim plugin) {
        this.plugin = plugin;
    }

    public boolean hasCooldown(UUID playerUUID) {
        if (!plugin.getConfigManager().isCooldownEnabled()) {
            return false;
        }

        Long lastClaim = cooldowns.get(playerUUID);
        if (lastClaim == null) {
            return false;
        }

        long elapsed = (System.currentTimeMillis() - lastClaim) / 1000;
        if (elapsed >= plugin.getConfigManager().getCooldownSeconds()) {
            cooldowns.remove(playerUUID);
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(UUID playerUUID) {
        if (!plugin.getConfigManager().isCooldownEnabled()) {
            return 0;
        }

        Long lastClaim = cooldowns.get(playerUUID);
        if (lastClaim == null) {
            return 0;
        }

        long elapsed = (System.currentTimeMillis() - lastClaim) / 1000;
        int remaining = (int) (plugin.getConfigManager().getCooldownSeconds() - elapsed);
        if (remaining <= 0) {
            cooldowns.remove(playerUUID);
            return 0;
        }
        return remaining;
    }

    public void setCooldown(UUID playerUUID) {
        cooldowns.put(playerUUID, System.currentTimeMillis());
    }

    public void removeCooldown(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }
}
