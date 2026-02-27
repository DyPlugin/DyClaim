package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final DyClaim plugin;
    private Economy economy;
    private boolean vaultAvailable;

    public EconomyManager(DyClaim plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault bulunamadı! Ekonomi özellikleri devre dışı.");
            vaultAvailable = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Ekonomi eklentisi bulunamadı! Vault yüklü ama ekonomi sağlayıcısı yok.");
            vaultAvailable = false;
            return;
        }

        economy = rsp.getProvider();
        vaultAvailable = true;
        plugin.getLogger().info("Vault ekonomi sistemi bağlandı: " + economy.getName());
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
        return economy.getBalance(player);
    }

    public boolean hasEnough(Player player, double amount) {
        if (!isAvailable())
            return true;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable())
            return true;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!isAvailable())
            return true;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    public String formatMoney(double amount) {
        if (isAvailable()) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }
}
