package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigManager {

    private final DyClaim plugin;
    private FileConfiguration effective;

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
        FileConfiguration config = loadValidated();
        this.effective = config;

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

    public FileConfiguration values() { return effective; }

    private FileConfiguration loadValidated() {
        try {
            File file = new File(plugin.getDataFolder(), "config.yml");
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file);
            if(!candidate.contains("confirmations.timeout-seconds"))candidate.set("confirmations.timeout-seconds",candidate.getLong("confirmation-timeout",30));
            try (var input = plugin.getResource("config.yml")) {
                if (input != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
                    candidate.setDefaults(defaults); candidate.options().copyDefaults(true);
                }
            }
            validate(candidate);
            File temp = new File(plugin.getDataFolder(), "config.yml.tmp");
            candidate.save(temp);
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            plugin.reloadConfig();
            return candidate;
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid configuration; previous valid configuration retained: " + ex.getMessage(), ex); }
    }

    public static void validate(FileConfiguration config) {
        if(config.getDefaults()!=null)for(String key:config.getDefaults().getKeys(true)) {
            Object expected=config.getDefaults().get(key),actual=config.get(key);
            if(expected instanceof Boolean && !(actual instanceof Boolean))throw new IllegalArgumentException(key+" must be boolean");
            if(expected instanceof Number && !(actual instanceof Number))throw new IllegalArgumentException(key+" must be numeric");
        }
        for (String key : List.of("economy.claim-price","economy.previous-claim-price","market.minimum-price","market.maximum-price")) {
            double value=config.getDouble(key); if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException(key);
        }
        if(config.getInt("economy.sell-refund-percent")<0 || config.getInt("economy.sell-refund-percent")>100)throw new IllegalArgumentException("economy.sell-refund-percent");
        double tax=config.getDouble("market.tax-percent"); if(!Double.isFinite(tax)||tax<0||tax>100)throw new IllegalArgumentException("market.tax-percent");
        if(config.getDouble("market.minimum-price")<=0 || config.getDouble("market.maximum-price")<config.getDouble("market.minimum-price"))throw new IllegalArgumentException("market limits");
        for(String key:List.of("confirmation-timeout","confirmations.timeout-seconds","claim.max-claims-per-player","visualization.duration-seconds","visualization.max-particles-per-run",
                "visualization.max-distance","trust.maximum-duration-days","admin.purge.batch-size","admin.purge.inactive-days","admin.purge.check-interval-minutes","warnings.cooldown-seconds","warnings.window-seconds","warnings.required-count","protection.mobs.remove-on-entry.interval-ticks","protection.mobs.remove-on-entry.batch-size")) {
            long value=config.getLong(key); if(value<=0||value>Integer.MAX_VALUE/20)throw new IllegalArgumentException(key);
        }
        for(String key:List.of("cooldown.seconds","teleport.warmup-seconds","claim-rules.spacing.minimum-empty-chunks")) if(config.getLong(key)<0||config.getLong(key)>Integer.MAX_VALUE/20)throw new IllegalArgumentException(key);
        if(!List.of("auto","en","tr").contains(config.getString("lang")))throw new IllegalArgumentException("lang");
        if(!"sink".equals(config.getString("market.tax-destination")))throw new IllegalArgumentException("Only tax-destination: sink is supported");
        if(config.getInt("ownership.max-coowners")!=1)throw new IllegalArgumentException("max-coowners must be 1");
        if(config.getInt("names.maximum-length")<1||config.getInt("names.maximum-length")>64)throw new IllegalArgumentException("names.maximum-length");
        Particle.valueOf(config.getString("visualization.particle"));
        if(!Material.valueOf(config.getString("visualization.bedrock-block")).isBlock())throw new IllegalArgumentException("visualization.bedrock-block");
        Sound.valueOf(config.getString("warnings.sound"));
        for(String key:List.of("warnings.sound-volume","warnings.sound-pitch")) {
            double value=config.getDouble(key);if(!Double.isFinite(value)||value<0||value>4)throw new IllegalArgumentException(key);
        }
        if(!List.of("hostile","all-mobs","list").contains(config.getString("protection.mobs.remove-on-entry.entity-scope")))throw new IllegalArgumentException("entity-scope");
        for(String key:List.of("excluded-types","included-types")) for(String type:config.getStringList("protection.mobs.remove-on-entry."+key))EntityType.valueOf(type);
        if(!List.of("server-ban","claim-entry-ban").contains(config.getString("warnings.punishment.type")))throw new IllegalArgumentException("punishment.type");
        dev.dyclaim.util.Rules.expiry(config.getString("warnings.punishment.duration"),0,Long.MAX_VALUE,false);
        for(String key:config.getKeys(true)) {
            if(key.endsWith("forced-value")&&config.get(key)!=null&&!(config.get(key) instanceof Boolean))throw new IllegalArgumentException(key);
            if(key.endsWith("player-toggle")&&!(config.get(key) instanceof Boolean))throw new IllegalArgumentException(key);
            if(key.startsWith("claim-rules.worlds.")&&key.endsWith("chunk-price")) {
                double price=config.getDouble(key);if(!Double.isFinite(price)||price<0)throw new IllegalArgumentException(key);
            }
            if(key.startsWith("claim-rules.worlds.")&&key.endsWith("claim-limit")&&config.getInt(key)<=0)throw new IllegalArgumentException(key);
        }
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
        effective = config;
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
        if(!Double.isFinite(claimPrice)||claimPrice<0)throw new IllegalArgumentException("Invalid price");
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
        if(cooldownSeconds<0||cooldownSeconds>Integer.MAX_VALUE/20)throw new IllegalArgumentException("Invalid cooldown");
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
