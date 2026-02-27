package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final DyClaim plugin;

    private String prefix;
    private String lang;

    private boolean economyEnabled;
    private double claimPrice;
    private double previousClaimPrice;
    private boolean autoRefundPriceDifference;
    private int sellRefundPercent;

    private boolean cooldownEnabled;
    private int cooldownSeconds;

    private int maxClaimsPerPlayer;
    private boolean allowClaiming;

    private int teleportWarmup;

    private boolean pvpDisabled;
    private boolean explosionDisabled;
    private boolean mobGriefingDisabled;

    private int visualizationDuration;
    private String particleType;
    private String bedrockBlock;

    private int confirmationTimeout;

    private boolean useActionbar;
    private boolean showEnter;
    private boolean showLeave;

    private List<String> blacklistedWorlds;

    public ConfigManager(DyClaim plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.prefix = config.getString("prefix", "&7[&eDy&6Claim&7]");
        this.lang = config.getString("lang", "auto");

        this.economyEnabled = config.getBoolean("economy.enabled", true);
        this.claimPrice = config.getDouble("economy.claim-price", 1500);
        this.previousClaimPrice = config.getDouble("economy.previous-claim-price", this.claimPrice);
        this.autoRefundPriceDifference = config.getBoolean("economy.auto-refund-price-difference", false);
        this.sellRefundPercent = config.getInt("economy.sell-refund-percent", 60);

        this.cooldownEnabled = config.getBoolean("cooldown.enabled", false);
        this.cooldownSeconds = config.getInt("cooldown.seconds", 0);

        this.maxClaimsPerPlayer = config.getInt("claim.max-claims-per-player", 10);
        this.allowClaiming = config.getBoolean("claim.allow-claiming", true);

        this.teleportWarmup = config.getInt("teleport.warmup-seconds", 3);

        this.pvpDisabled = config.getBoolean("protection.pvp-disabled", true);
        this.explosionDisabled = config.getBoolean("protection.explosion-disabled", true);
        this.mobGriefingDisabled = config.getBoolean("protection.mob-griefing-disabled", true);

        this.visualizationDuration = config.getInt("visualization.duration-seconds", 10);
        this.particleType = config.getString("visualization.particle", "FLAME");
        this.bedrockBlock = config.getString("visualization.bedrock-block", "ORANGE_STAINED_GLASS");

        this.confirmationTimeout = config.getInt("confirmation-timeout", 30);

        this.useActionbar = config.getBoolean("notification.use-actionbar", true);
        this.showEnter = config.getBoolean("notification.show-enter", true);
        this.showLeave = config.getBoolean("notification.show-leave", true);

        this.blacklistedWorlds = config.getStringList("blacklisted-worlds");
        if (this.blacklistedWorlds == null)
            this.blacklistedWorlds = new ArrayList<>();
    }

    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("prefix", prefix);
        config.set("lang", lang);
        config.set("economy.enabled", economyEnabled);
        config.set("economy.claim-price", claimPrice);
        config.set("economy.previous-claim-price", previousClaimPrice);
        config.set("economy.auto-refund-price-difference", autoRefundPriceDifference);
        config.set("economy.sell-refund-percent", sellRefundPercent);
        config.set("cooldown.enabled", cooldownEnabled);
        config.set("cooldown.seconds", cooldownSeconds);
        config.set("claim.max-claims-per-player", maxClaimsPerPlayer);
        config.set("claim.allow-claiming", allowClaiming);
        config.set("protection.pvp-disabled", pvpDisabled);
        config.set("protection.explosion-disabled", explosionDisabled);
        config.set("protection.mob-griefing-disabled", mobGriefingDisabled);
        config.set("blacklisted-worlds", blacklistedWorlds);
        config.set("notification.use-actionbar", useActionbar);
        config.set("notification.show-enter", showEnter);
        config.set("notification.show-leave", showLeave);
        plugin.saveConfig();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLang() {
        return lang;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    public double getClaimPrice() {
        return claimPrice;
    }

    public int getSellRefundPercent() {
        return sellRefundPercent;
    }

    public double getPreviousClaimPrice() {
        return previousClaimPrice;
    }

    public boolean isAutoRefundPriceDifference() {
        return autoRefundPriceDifference;
    }

    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getMaxClaimsPerPlayer() {
        return maxClaimsPerPlayer;
    }

    public boolean isAllowClaiming() {
        return allowClaiming;
    }

    public int getTeleportWarmup() {
        return teleportWarmup;
    }

    public boolean isPvpDisabled() {
        return pvpDisabled;
    }

    public boolean isExplosionDisabled() {
        return explosionDisabled;
    }

    public boolean isMobGriefingDisabled() {
        return mobGriefingDisabled;
    }

    public int getVisualizationDuration() {
        return visualizationDuration;
    }

    public String getParticleType() {
        return particleType;
    }

    public String getBedrockBlock() {
        return bedrockBlock;
    }

    public int getConfirmationTimeout() {
        return confirmationTimeout;
    }

    public List<String> getBlacklistedWorlds() {
        return blacklistedWorlds;
    }

    public boolean isWorldBlacklisted(String worldName) {
        return blacklistedWorlds.contains(worldName);
    }

    public boolean isUseActionbar() {
        return useActionbar;
    }

    public boolean isShowEnter() {
        return showEnter;
    }

    public boolean isShowLeave() {
        return showLeave;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
        saveConfig();
    }

    public void setEconomyEnabled(boolean economyEnabled) {
        this.economyEnabled = economyEnabled;
        saveConfig();
    }

    public void setClaimPrice(double claimPrice) {
        this.previousClaimPrice = this.claimPrice;
        this.claimPrice = claimPrice;
        saveConfig();
    }

    public void setPreviousClaimPrice(double previousClaimPrice) {
        this.previousClaimPrice = previousClaimPrice;
        saveConfig();
    }

    public void setAutoRefundPriceDifference(boolean autoRefundPriceDifference) {
        this.autoRefundPriceDifference = autoRefundPriceDifference;
        saveConfig();
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
        this.cooldownEnabled = cooldownSeconds > 0;
        saveConfig();
    }

    public void setAllowClaiming(boolean allowClaiming) {
        this.allowClaiming = allowClaiming;
        saveConfig();
    }

    public void setLang(String lang) {
        this.lang = lang;
        saveConfig();
    }

    public boolean addBlacklistedWorld(String worldName) {
        if (blacklistedWorlds.contains(worldName))
            return false;
        blacklistedWorlds.add(worldName);
        saveConfig();
        return true;
    }

    public boolean removeBlacklistedWorld(String worldName) {
        boolean removed = blacklistedWorlds.remove(worldName);
        if (removed)
            saveConfig();
        return removed;
    }
}
