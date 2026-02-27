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
    private final Map<String, String> messagesEn = new HashMap<>();
    private final Map<String, String> messagesTr = new HashMap<>();

    public MessageManager(DyClaim plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messagesEn.clear();
        messagesTr.clear();
        loadLanguage("en", messagesEn);
        loadLanguage("tr", messagesTr);
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
        if (configured.equals("tr") || configured.equals("en")) {
            return configured;
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
        Map<String, String> selected = lang.equals("tr") ? messagesTr : messagesEn;
        String value = selected.get(key);
        if (value != null) {
            return value;
        }
        String fallback = messagesEn.get(key);
        return fallback != null ? fallback : "&cMessage not found: " + key;
    }

    @SuppressWarnings("deprecation")
    public static String colorize(String text) {
        if (text == null)
            return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
