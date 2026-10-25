package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.*;

/**
 * Converts GriefPrevention or ClaimChunk claims into DyClaim chunk claims.
 * The source plugin must be installed and enabled; its own data is never changed.
 */
public class ClaimImporter {
    /** One chunk to create, with the trust rights carried over from the source plugin. */
    public record Candidate(UUID owner,String ownerName,String world,int x,int z,Map<UUID,Set<String>> trust) {
        String key(){return world+":"+x+":"+z;}
    }
    public record Plan(String source,int sourceClaims,List<Candidate> claims,int alreadyClaimed,int blacklisted,int conflicts) {}
    public record Result(int created,int skipped,int trusted) {}

    /** Reads one plugin's claims; implementations live in separate classes so absent APIs never load. */
    public interface Source {
        String id();
        /** Returns the number of source claims and adds one candidate per covered chunk. */
        int read(List<Candidate> out);
    }

    private final DyClaim plugin;
    public ClaimImporter(DyClaim plugin){this.plugin=plugin;}

    public static List<String> sources(){return List.of("griefprevention","claimchunk");}

    public Source source(String id){
        String name=switch(id.toLowerCase(Locale.ROOT)){case "griefprevention","gp"->"GriefPrevention";case "claimchunk","cc"->"ClaimChunk";default->null;};
        if(name==null)throw new IllegalArgumentException("source");
        Plugin source=Bukkit.getPluginManager().getPlugin(name);
        if(source==null||!source.isEnabled())return null;
        return name.equals("GriefPrevention")?new dev.dyclaim.hook.GriefPreventionImport():new dev.dyclaim.hook.ClaimChunkImport(source);
    }

    public Plan plan(Source source){
        List<Candidate> raw=new ArrayList<>();int count=source.read(raw);
        Map<String,Candidate> chosen=new LinkedHashMap<>();int claimed=0,blacklisted=0,conflicts=0;
        for(Candidate candidate:raw){
            if(plugin.getConfigManager().isWorldBlacklisted(candidate.world())){blacklisted++;continue;}
            if(plugin.getClaimManager().isChunkClaimed(candidate.world(),candidate.x(),candidate.z())){claimed++;continue;}
            Candidate previous=chosen.get(candidate.key());
            if(previous==null){chosen.put(candidate.key(),candidate);continue;}
            // Two source claims of the same owner touching one chunk merge; different owners keep the first.
            if(previous.owner().equals(candidate.owner())){
                Map<UUID,Set<String>> trust=new HashMap<>();
                previous.trust().forEach((uuid,rights)->trust.computeIfAbsent(uuid,k->new HashSet<>()).addAll(rights));
                candidate.trust().forEach((uuid,rights)->trust.computeIfAbsent(uuid,k->new HashSet<>()).addAll(rights));
                chosen.put(candidate.key(),new Candidate(previous.owner(),previous.ownerName(),previous.world(),previous.x(),previous.z(),trust));
            }else conflicts++;
        }
        return new Plan(source.id(),count,List.copyOf(chosen.values()),claimed,blacklisted,conflicts);
    }

    /** Creates the planned claims after a backup; chunks claimed in the meantime are skipped. */
    public Result apply(Plan plan){
        plugin.getClaimManager().backup("import-"+plan.source());
        int created=0,skipped=0,trusted=0;
        for(Candidate candidate:plan.claims()){
            ClaimData claim=plugin.getClaimManager().importClaim(candidate.owner(),candidate.ownerName(),candidate.world(),candidate.x(),candidate.z());
            if(claim==null){skipped++;continue;}
            created++;
            for(var entry:candidate.trust().entrySet()){
                if(entry.getKey().equals(candidate.owner())||entry.getValue().isEmpty())continue;
                claim.grantRights(entry.getKey(),entry.getValue());trusted++;
            }
        }
        plugin.getClaimManager().saveAllSync();
        return new Result(created,skipped,trusted);
    }

    /** Every chunk that shares at least one block column with the rectangle. */
    public static void chunksOf(String world,int minX,int minZ,int maxX,int maxZ,java.util.function.BiConsumer<Integer,Integer> chunk){
        for(int cx=Math.min(minX,maxX)>>4;cx<=Math.max(minX,maxX)>>4;cx++)
            for(int cz=Math.min(minZ,maxZ)>>4;cz<=Math.max(minZ,maxZ)>>4;cz++)chunk.accept(cx,cz);
    }
    /** The source's stored name, then the server's last known name, then the UUID. */
    public static String nameOf(UUID uuid,String fallback){
        String name=fallback;
        if(name==null||name.isBlank()){var offline=Bukkit.getOfflinePlayer(uuid);name=offline==null?null:offline.getName();}
        return name==null||name.isBlank()?uuid.toString():name;
    }
}
