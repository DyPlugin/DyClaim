package dev.dyclaim.util;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/** Modrinth transport and compatible stable-release selection. No Bukkit calls. */
public final class UpdateFeed {
    private static final Set<String> LOADERS = Set.of("paper", "purpur", "spigot", "bukkit");
    private static final Pattern GAME_VERSION = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)(?:$|[-.])");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    public record Release(String version, String url) {}
    public enum Target { BUKKIT, FOLIA, SPONGE, VELOCITY, BUNGEECORD }
    public static URL requestUrl(String gameVersion,Target target)throws MalformedURLException {
        java.util.Objects.requireNonNull(target);
        if(target==Target.BUKKIT)return requestUrl(gameVersion);
        String loaders=URLEncoder.encode(new Gson().toJson(new String[]{target.name().toLowerCase(java.util.Locale.ROOT)}),StandardCharsets.UTF_8);
        String games=URLEncoder.encode(new Gson().toJson(new String[]{gameVersion}),StandardCharsets.UTF_8);
        return new URL("https://api.modrinth.com/v2/project/dyclaim/version?loaders="+loaders+"&game_versions="+games+"&include_changelog=false");
    }
    private UpdateFeed() {}
    public static String gameVersion(String bukkitVersion) {
        var matcher = GAME_VERSION.matcher(bukkitVersion);
        if (!matcher.find()) throw new IllegalArgumentException("Unknown server version: " + bukkitVersion);
        return matcher.group(1);
    }
    public static URL requestUrl(String gameVersion) throws MalformedURLException {
        String loaders = URLEncoder.encode("[\"purpur\",\"paper\",\"spigot\",\"bukkit\"]", StandardCharsets.UTF_8);
        String games = URLEncoder.encode(new Gson().toJson(new String[]{gameVersion}), StandardCharsets.UTF_8);
        return new URL("https://api.modrinth.com/v2/project/dyclaim/version?loaders=" + loaders
                + "&game_versions=" + games + "&include_changelog=false");
    }
    public static String fetch(URL url, String installedVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "DyPlugin/DyClaim/" + installedVersion + " (github.com/DyPlugin/DyClaim)");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Modrinth returned HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                byte[] response = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.length > MAX_RESPONSE_BYTES) throw new IOException("Update response exceeds size limit");
                return new String(response, StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
    public static Release select(String json, String gameVersion, String installedVersion) {
        return select(json,gameVersion,installedVersion,Target.BUKKIT);
    }
    public static Release select(String json,String gameVersion,String installedVersion,Target target) {
        java.util.Objects.requireNonNull(target);
        Set<String> accepted=target==Target.BUKKIT?LOADERS:Set.of(target.name().toLowerCase(java.util.Locale.ROOT));
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        Release best = null;
        for (JsonElement element : versions) {
            try {
                JsonObject candidate = element.getAsJsonObject();
                if (!"listed".equals(candidate.get("status").getAsString())
                        || !"release".equals(candidate.get("version_type").getAsString())
                        || !contains(candidate.getAsJsonArray("game_versions"), Set.of(gameVersion))
                        || !contains(candidate.getAsJsonArray("loaders"), accepted)) continue;
                String version = candidate.get("version_number").getAsString();
                if (!version.matches("(?i)v?\\d+\\.\\d+\\.\\d+") || !ReleaseVersion.newer(version, installedVersion)
                        || (best != null && !ReleaseVersion.newer(version, best.version()))) continue;
                if(!matchingArtifact(candidate,version,target))continue;
                String id = candidate.get("id").getAsString();
                if (!id.matches("[A-Za-z0-9]+")) continue;
                best = new Release(version, "https://modrinth.com/plugin/dyclaim/version/" + id);
            } catch (RuntimeException ignored) {
                // One malformed entry must not hide valid releases in the same response.
            }
        }
        return best;
    }
    private static boolean matchingArtifact(JsonObject candidate,String version,Target target){
        String suffix=switch(target){case BUKKIT->"";case FOLIA->"-Folia";case SPONGE->"-Sponge";case VELOCITY->"-Velocity";case BUNGEECORD->"-BungeeCord";};
        String expected="DyClaim-v"+version.replaceFirst("(?i)^v","")+suffix+".jar";
        JsonArray files=candidate.getAsJsonArray("files");if(files==null)return target==Target.BUKKIT;
        for(JsonElement file:files)try{if(expected.equalsIgnoreCase(file.getAsJsonObject().get("filename").getAsString()))return true;}catch(RuntimeException ignored){}
        return false;
    }
    private static boolean contains(JsonArray values, Set<String> accepted) {
        for (JsonElement value : values) if (accepted.contains(value.getAsString())) return true;
        return false;
    }
}
