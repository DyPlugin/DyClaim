package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import java.util.UUID;

public class EconomyManager {
    private final DyClaim plugin;
    private final ThreadLocal<Boolean> uncertain=ThreadLocal.withInitial(()->false);
    private final java.util.function.Supplier<Economy> providerLookup;
    // A method reference to registeredProvider would eagerly link its optional Vault return type.
    public EconomyManager(DyClaim plugin) { this.plugin=plugin;this.providerLookup=null; }
    public EconomyManager(DyClaim plugin,java.util.function.Supplier<Economy> providerLookup) { this.plugin=plugin;this.providerLookup=java.util.Objects.requireNonNull(providerLookup); }
    private Economy provider() {
        return providerLookup==null?registeredProvider():providerLookup.get();
    }
    private static Economy registeredProvider() {
        if(Bukkit.getPluginManager().getPlugin("Vault")==null)return null;
        var registration=Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration==null ? null : registration.getProvider();
    }
    public boolean isAvailable() { try{return provider()!=null;}catch(LinkageError ex){return false;} }
    public boolean isEnabled() { return plugin.getConfigManager().isEconomyEnabled()&&isAvailable(); }
    public double getBalance(OfflinePlayer player) { try{Economy e=provider();return e==null?0:e.getBalance(player);}catch(Exception|LinkageError ex){return 0;} }
    public boolean hasEnough(OfflinePlayer player,double amount) {
        if(!Double.isFinite(amount)||amount<0)return false;
        try{Economy e=provider();return e!=null&&e.has(player,amount);}catch(Exception|LinkageError ex){return false;}
    }
    public boolean withdraw(OfflinePlayer player,double amount) {return transaction(player,amount,true);}
    public boolean deposit(OfflinePlayer player,double amount) {return transaction(player,amount,false);}
    public boolean withdraw(UUID uuid,double amount) {return withdraw(Bukkit.getOfflinePlayer(uuid),amount);}
    public boolean deposit(UUID uuid,double amount) {return deposit(Bukkit.getOfflinePlayer(uuid),amount);}
    public boolean wasUncertain() {return uncertain.get();}
    private boolean transaction(OfflinePlayer player,double amount,boolean withdraw) {
        uncertain.remove();
        if(!Double.isFinite(amount)||amount<0)return false;
        if(amount==0)return true;
        try {
            Economy e=provider();if(e==null)return false;
            EconomyResponse response=withdraw?e.withdrawPlayer(player,amount):e.depositPlayer(player,amount);
            if(response==null){uncertain.set(true);return false;}
            return response.transactionSuccess();
        } catch(Exception|LinkageError ex) {
            uncertain.set(true);plugin.getLogger().severe("Economy response uncertain: "+ex.getMessage());return false;
        }
    }
    public String formatMoney(double amount) {
        try{Economy e=provider();if(e!=null)return e.format(amount);}catch(Exception|LinkageError ignored){}
        return String.format(java.util.Locale.ROOT,"%.2f",amount);
    }
}
