package dev.dyclaim.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dyclaim.DyClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class UpdateChecker implements Listener {

    private final DyClaim plugin;
    private final Gson gson = new Gson();
    private String latestVersion = null;
    private String downloadUrl = null;
    private boolean updateAvailable = false;

    public UpdateChecker(DyClaim plugin) {
        this.plugin = plugin;
    }

    public void check() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String projectId = plugin.getConfig().getString("update-checker.modrinth-id", "");
                if (projectId == null || projectId.isBlank()) {
                    return;
                }

                String serverVersion = Bukkit.getMinecraftVersion();

                String apiUrl = "https://api.modrinth.com/v2/project/" + projectId
                        + "/version?loaders=[\"purpur\",\"paper\",\"spigot\",\"bukkit\"]"
                        + "&game_versions=[\"" + serverVersion + "\"]"
                        + "&limit=1";

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "DyClaim/" + plugin.getDescription().getVersion());
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String json = response.toString();
                    parseResponse(json, projectId);
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            }
        });
    }

    private void parseResponse(String json, String projectId) {
        try {
            JsonArray array = gson.fromJson(json, JsonArray.class);
            if (array != null && !array.isEmpty()) {
                JsonObject latest = array.get(0).getAsJsonObject();
                String versionNumber = latest.get("version_number").getAsString();
                String currentVersion = plugin.getDescription().getVersion();
                if (!versionNumber.equals(currentVersion)) {
                    latestVersion = versionNumber;
                    downloadUrl = "https://modrinth.com/plugin/" + projectId;
                    updateAvailable = true;

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("New version available: DyClaim-" + latestVersion);
                        plugin.getLogger().info("Download: " + downloadUrl);
                    });
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to parse update response: " + e.getMessage());
        }
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        if (!updateAvailable)
            return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dyclaim.admin"))
            return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(player, "update-available",
                        Map.of("{version}", "DyClaim-v" + latestVersion)));
                player.sendMessage(plugin.getMessageManager().getMessage(player, "update-download",
                        Map.of("{link}", downloadUrl)));
            }
        }, 60L);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
