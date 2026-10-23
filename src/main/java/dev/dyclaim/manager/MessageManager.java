package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MessageManager {

    private final DyClaim plugin;
    private record Catalog(Map<String,String> en,Map<String,String> tr) {}
    private volatile Catalog catalog=new Catalog(Map.of(),Map.of());

    public MessageManager(DyClaim plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        Map<String,String> en=new HashMap<>(),tr=new HashMap<>();
        loadLanguage("en", en);
        loadLanguage("tr", tr);
        // Publish both complete languages together; readers retain the previous catalog during I/O.
        catalog=new Catalog(Map.copyOf(en),Map.copyOf(tr));
    }

    public String getMessage(String key) {
        String msg = getMessageInternal(resolveConfiguredLanguage(), key);
        return colorize(msg);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessageInternal(resolveConfiguredLanguage(), key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace(entry.getKey(), entry.getValue());
        }
        return colorize(msg);
    }

    public String getMessage(CommandSender sender, String key) {
        String msg = getMessageInternal(resolveLanguage(sender), key);
        return colorize(msg);
    }

    public String getMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = getMessageInternal(resolveLanguage(sender), key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace(entry.getKey(), entry.getValue());
        }
        return colorize(msg);
    }

    public String getRawMessage(String key) {
        return getMessageInternal(resolveConfiguredLanguage(), key);
    }

    public String getPrefix() {
        return colorize(plugin.getConfigManager().getPrefix()) + " ";
    }

    public String getPrefixed(String key) {
        return getPrefix() + getMessage(key);
    }

    public String getPrefixed(String key, Map<String, String> placeholders) {
        return getPrefix() + getMessage(key, placeholders);
    }

    public String getPrefixed(CommandSender sender, String key) {
        return getPrefix() + getMessage(sender, key);
    }

    public String getPrefixed(CommandSender sender, String key, Map<String, String> placeholders) {
        return getPrefix() + getMessage(sender, key, placeholders);
    }

    public String getPreferredLanguage(CommandSender sender) {
        return resolveLanguage(sender);
    }

    private void loadLanguage(String lang, Map<String, String> target) {
        String fileName = "messages_" + lang + ".yml";
        File messagesFile = new File(plugin.getDataFolder(), fileName);
        if (!messagesFile.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        if (!messagesFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesFile);
        YamlConfiguration defConfig = null;
        InputStream defStream = plugin.getResource(fileName);
        if (defStream != null) {
            InputStreamReader defReader = new InputStreamReader(defStream, StandardCharsets.UTF_8);
            defConfig = YamlConfiguration.loadConfiguration(defReader);
            config.setDefaults(defConfig);
        }

        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                target.put(key, config.getString(key));
            }
        }

        if (defConfig != null) {
            for (String key : defConfig.getKeys(true)) {
                if (!target.containsKey(key) && defConfig.isString(key)) {
                    target.put(key, defConfig.getString(key));
                }
            }
            refreshPreviousDefaults(lang, target, defConfig);
            upgradeShippedTexts(lang, target, defConfig);
        }
    }

    /** Texts still exactly as an earlier release shipped them follow the current wording; edited texts stay. */
    private void upgradeShippedTexts(String lang, Map<String,String> target, YamlConfiguration defaults) {
        InputStream stream = plugin.getResource("message-history_" + lang + ".yml");
        if (stream == null) return;
        YamlConfiguration history = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String key : history.getKeys(false)) {
            String current = defaults.getString(key);
            if (current != null && history.getStringList(key).contains(target.get(key))) target.put(key, current);
        }
    }

    /** Refresh only recognized shipped defaults; preserve server-customized messages. */
    private void refreshPreviousDefaults(String lang, Map<String,String> target, YamlConfiguration defaults) {
        boolean tr = lang.equals("tr");
        for (String key : java.util.List.of("help-list","help-trust","help-tp","trust-usage","tp-usage","tp-invalid-number",
                "list-named-entry","trustperm-usage","coowner-usage","transfer-usage","market-usage","ban-usage","purge-usage","warn-usage")) {
            String current = defaults.getString(key);
            if (current == null) continue;
            String previous = switch (key) {
                case "help-list" -> current.replace(tr ? " [sayfa]" : " [page]", "");
                case "help-trust", "trust-usage" -> current.replace(tr ? " [süre]" : " [duration]", "");
                case "help-tp" -> current.replace(tr ? "ışınlan <numara|isim>" : "tp <number|name>", tr ? "tp <numara>" : "tp <number>");
                case "tp-usage" -> current.replace(tr ? "ışınlan <1-{max}|isim>" : "tp <1-{max}|name>", "tp <1-{max}>");
                case "tp-invalid-number" -> tr ? current.replace("/claim liste", "/claim list") : current;
                case "list-named-entry" -> "&7{number}. {name} | {chunk}";
                case "coowner-usage" -> tr
                        ? "&e/claim ortak ekle <oyuncu> | çıkar. Ortak sahip satış, devir, silme veya sahip değiştirme yapamaz."
                        : "&e/claim coowner add <player> | remove. A coowner cannot sell, transfer, delete or replace owners.";
                default -> current.replace(tr ? "&cKullanım: " : "&cUsage: ", "");
            };
            if (previous.equals(target.get(key))) target.put(key, current);
        }
        // Upgrade known shipped command text in memory; never overwrite custom translations.
        for(String key:java.util.List.of("help-explosion","help-mobexplosion","help-trustperm","trustperm-usage")) {
            String current=defaults.getString(key);if(current==null)continue;
            String previous=switch(key) {
                case "help-explosion" -> tr ? "&6/claim patlama &7- Patlama aç/kapat (claim'inizde)" : "&6/claim explosion &7- Toggle explosions (in your claim)";
                case "help-mobexplosion" -> current.replace(tr?"/claim patlama mob":"/claim explosion mob",tr?"/claim mobpatlama":"/claim mobexplosion");
                default -> current.replace(tr?"/claim güven ":"/claim trust ",tr?"/claim güvenizin ":"/claim trustperm ");
            };
            String stored=target.get(key);
            if(previous.equals(stored)||(key.equals("trustperm-usage")&&previous.replace(tr?"&cKullanım: ":"&cUsage: ","").equals(stored)))target.put(key,current);
        }
    }

    private String resolveConfiguredLanguage() {
        String configured = plugin.getConfigManager().getLang().toLowerCase(Locale.ROOT);
        if (configured.equals("tr") || configured.equals("en")) {
            return configured;
        }
        return "en";
    }

    private String resolveLanguage(CommandSender sender) {
        String configured = plugin.getConfigManager().getLang().toLowerCase(Locale.ROOT);
        if (configured.equals("tr") || configured.equals("en")) return configured;
        if(sender instanceof Player player && plugin.getFeatureManager()!=null) {
            String selected=plugin.getFeatureManager().language(player.getUniqueId());
            if(selected!=null)return selected;
        }
        if (sender instanceof Player player) {
            String locale = player.getLocale();
            if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr")) {
                return "tr";
            }
        }
        return "en";
    }

    private String getMessageInternal(String lang, String key) {
        Catalog view=catalog;
        Map<String, String> selected = lang.equals("tr") ? view.tr() : view.en();
        String value = selected.get(key);
        if (value != null) {
            return value;
        }
        String fallback = view.en().get(key);
        return fallback != null ? fallback : "&cMessage not found: " + key;
    }

    @SuppressWarnings("deprecation")
    public static String colorize(String text) {
        if (text == null)
            return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

