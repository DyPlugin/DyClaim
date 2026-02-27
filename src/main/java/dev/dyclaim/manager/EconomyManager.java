package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class EconomyManager {

    private final DyClaim plugin;
    private Object economy;
    private boolean vaultAvailable;

    public EconomyManager(DyClaim plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found. Economy features disabled.");
            vaultAvailable = false;
            return;
        }

        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (rsp == null) {
                plugin.getLogger()
                        .warning("No economy provider found! Vault is installed but no economy plugin is available.");
                vaultAvailable = false;
                return;
            }

            Method getProvider = rsp.getClass().getMethod("getProvider");
            economy = getProvider.invoke(rsp);
            vaultAvailable = true;

            Method getName = economy.getClass().getMethod("getName");
            plugin.getLogger().info("Vault economy hooked: " + getName.invoke(economy));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook Vault economy: " + e.getMessage());
            vaultAvailable = false;
        }
    }

    public boolean isAvailable() {
        return vaultAvailable && economy != null;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().isEconomyEnabled() && isAvailable();
    }

    public double getBalance(Player player) {
        if (!isAvailable())
            return 0;
        try {
            Method m = economy.getClass().getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            return (double) m.invoke(economy, player);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean hasEnough(Player player, double amount) {
        if (!isAvailable())
            return true;
        try {
            Method m = economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class);
            return (boolean) m.invoke(economy, player, amount);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable())
            return true;
        try {
            Method m = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = m.invoke(economy, player, amount);
            Method success = response.getClass().getMethod("transactionSuccess");
            return (boolean) success.invoke(response);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean deposit(Player player, double amount) {
        if (!isAvailable())
            return true;
        try {
            Method m = economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = m.invoke(economy, player, amount);
            Method success = response.getClass().getMethod("transactionSuccess");
            return (boolean) success.invoke(response);
        } catch (Exception e) {
            return true;
        }
    }

    public String formatMoney(double amount) {
        if (isAvailable()) {
            try {
                Method m = economy.getClass().getMethod("format", double.class);
                return (String) m.invoke(economy, amount);
            } catch (Exception e) {
                return String.format("%.2f", amount);
            }
        }
        return String.format("%.2f", amount);
    }
}
