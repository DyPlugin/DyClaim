package dev.dyclaim;

import dev.dyclaim.command.ClaimCommand;
import dev.dyclaim.command.UnclaimCommand;
import dev.dyclaim.hook.ClaimPluginHooks;
import dev.dyclaim.hook.GriefPreventionHook;
import dev.dyclaim.hook.WorldGuardHook;
import dev.dyclaim.listener.ClaimProtectionListener;
import dev.dyclaim.listener.PlayerListener;
import dev.dyclaim.manager.*;
import dev.dyclaim.visualizer.ChunkVisualizer;
import org.bukkit.plugin.java.JavaPlugin;

public class DyClaim extends JavaPlugin {

    private static DyClaim instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ClaimManager claimManager;
    private EconomyManager economyManager;
    private CooldownManager cooldownManager;
    private ConfirmationManager confirmationManager;
    private ChunkVisualizer chunkVisualizer;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages_en.yml", false);
        saveResource("messages_tr.yml", false);

        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.claimManager = new ClaimManager(this);
        this.economyManager = new EconomyManager(this);
        this.cooldownManager = new CooldownManager(this);
        this.confirmationManager = new ConfirmationManager(this);
        this.chunkVisualizer = new ChunkVisualizer(this);

        WorldGuardHook.init();
        GriefPreventionHook.init();
        ClaimPluginHooks.init(this);

        ClaimCommand claimCommand = new ClaimCommand(this);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("claim").setTabCompleter(claimCommand);

        UnclaimCommand unclaimCommand = new UnclaimCommand(this);
        getCommand("unclaim").setExecutor(unclaimCommand);

        getCommand("onayla").setExecutor((sender, cmd, lbl, a) -> {
            if (sender instanceof org.bukkit.entity.Player p) {
                if (!confirmationManager.confirm(p.getUniqueId())) {
                    p.sendMessage(messageManager.getPrefixed(p, "confirm-none"));
                }
            }
            return true;
        });

        getCommand("reddet").setExecutor((sender, cmd, lbl, a) -> {
            if (sender instanceof org.bukkit.entity.Player p) {
                if (!confirmationManager.deny(p.getUniqueId())) {
                    p.sendMessage(messageManager.getPrefixed(p, "confirm-none"));
                }
            }
            return true;
        });

        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        if (getConfig().getBoolean("update-checker.enabled", true)) {
            this.updateChecker = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            updateChecker.check();
        }

        getLogger().info("DyClaim v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (claimManager != null) {
            claimManager.saveAllSync();
        }
        if (chunkVisualizer != null) {
            chunkVisualizer.cleanupAll();
        }
        getLogger().info("DyClaim disabled!");
    }

    public static DyClaim getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public ChunkVisualizer getChunkVisualizer() {
        return chunkVisualizer;
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
    }
}
