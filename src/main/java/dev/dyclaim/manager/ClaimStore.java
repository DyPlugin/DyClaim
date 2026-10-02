package dev.dyclaim.manager;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.dyclaim.model.*;
import dev.dyclaim.util.AtomicJson;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ClaimStore {
    public static final int VERSION = 2;
    private final Path file;
    public ClaimStore(Path file) { this.file = file; }
    public Map<String, ClaimData> load(double legacyPrice) throws IOException {
        if (!Files.exists(file)) return new HashMap<>();
        JsonElement root = AtomicJson.read(file, JsonElement.class);
        if (!root.isJsonObject()) throw new IOException("Claims root must be an object");
        JsonObject object = root.getAsJsonObject();
        boolean legacy = !object.has("schemaVersion");
        Map<String, ClaimData> claims;
        try {
            if (!legacy && object.get("schemaVersion").getAsInt() != VERSION) throw new IOException("Unsupported claim schema");
            JsonElement content = legacy ? object : object.get("claims");
            claims = AtomicJson.GSON.fromJson(content, new TypeToken<Map<String, ClaimData>>(){}.getType());
            validate(claims);
            Set<UUID> ids = new HashSet<>();
            for (ClaimData claim : claims.values()) {
                claim.migrate(legacyPrice);
                if (!ids.add(claim.getId())) throw new IOException("Duplicate claim ID");
            }
        } catch (RuntimeException ex) { throw new IOException("Malformed claim record", ex); }
        if (legacy) {
            Path backup = file.resolveSibling("claims.beta-backup.json");
            if (!Files.exists(backup)) Files.copy(file, backup);
            save(claims);
        }
        return claims;
    }
    public void save(Map<String, ClaimData> claims) throws IOException {
        validate(claims);
        AtomicJson.write(file, Map.of("schemaVersion", VERSION, "claims", claims));
    }
    public static void validate(Map<String, ClaimData> claims) throws IOException {
        if (claims == null) throw new IOException("Missing claims map");
        for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
            ClaimData claim = entry.getValue();
            if (claim == null || claim.getOwnerUUID() == null || claim.getOwnerName() == null
                    || claim.getWorld() == null || claim.getWorld().isBlank() || !entry.getKey().equals(claim.getChunkKey())
                    || claim.getClaimedAt() < 0 || claim.getRevision() < 0) throw new IOException("Invalid claim: " + entry.getKey());
            if (claim.getOwnerUUID().equals(claim.getCoowner())) throw new IOException("Owner cannot also be coowner");
            for (Map.Entry<UUID, TrustGrant> grant : claim.getTrustGrants().entrySet()) {
                if (grant.getKey() == null || !validGrant(grant.getValue())) throw new IOException("Invalid trust: " + entry.getKey());
            }
            for (Map.Entry<String, TrustGrant> grant : claim.getClanGrants().entrySet()) {
                if (grant.getKey() == null || !validGrant(grant.getValue())) throw new IOException("Invalid clan trust");
            }
            if (!finiteMoney(claim.getPaidPrice()) || !finiteMoney(claim.getMarketPrice()) || !finiteMoney(claim.getRefundBasis(0))) throw new IOException("Invalid claim price");
            if (claim.hasSpawn() && (!Double.isFinite(claim.getSpawnX()) || !Double.isFinite(claim.getSpawnY()) || !Double.isFinite(claim.getSpawnZ())
                    || !Float.isFinite(claim.getSpawnYaw()) || !Float.isFinite(claim.getSpawnPitch())
                    || ((int)Math.floor(claim.getSpawnX()) >> 4) != claim.getChunkX()
                    || ((int)Math.floor(claim.getSpawnZ()) >> 4) != claim.getChunkZ())) throw new IOException("Invalid spawn");
        }
    }
    private static boolean finiteMoney(Double value) { return value == null || (Double.isFinite(value) && value >= 0); }
    private static boolean validGrant(TrustGrant grant) {
        return grant != null && grant.expiresAt >= 0 && grant.permissions != null && TrustGrant.RIGHTS.containsAll(grant.permissions);
    }
}
