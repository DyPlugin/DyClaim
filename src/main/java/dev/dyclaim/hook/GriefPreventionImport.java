package dev.dyclaim.hook;

import dev.dyclaim.manager.ClaimImporter;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import java.util.*;

/** Loaded only while GriefPrevention is enabled. Subdivisions and admin claims are skipped. */
public final class GriefPreventionImport implements ClaimImporter.Source {
    private static final Set<String> ALL=Set.of("build","containers","doors","trapdoors","redstone","entities","teleport");
    private static final Set<String> CONTAINER=Set.of("containers","entities","doors","trapdoors","redstone");
    private static final Set<String> ACCESS=Set.of("doors","trapdoors","redstone");
    public String id(){return "griefprevention";}
    public int read(List<ClaimImporter.Candidate> out){
        GriefPrevention gp=GriefPrevention.instance;
        if(gp==null||gp.dataStore==null)throw new IllegalStateException("GriefPrevention is not ready");
        int count=0;
        for(Claim claim:new ArrayList<>(gp.dataStore.getClaims())){
            if(claim.parent!=null||claim.isAdminClaim()||claim.getOwnerID()==null)continue;
            var lesser=claim.getLesserBoundaryCorner();var greater=claim.getGreaterBoundaryCorner();
            if(lesser==null||greater==null||lesser.getWorld()==null)continue;
            count++;
            UUID owner=claim.getOwnerID();String ownerName=ClaimImporter.nameOf(owner,claim.getOwnerName());
            Map<UUID,Set<String>> trust=trust(claim);String world=lesser.getWorld().getName();
            ClaimImporter.chunksOf(world,lesser.getBlockX(),lesser.getBlockZ(),greater.getBlockX(),greater.getBlockZ(),
                    (x,z)->out.add(new ClaimImporter.Candidate(owner,ownerName,world,x,z,trust)));
        }
        return count;
    }
    /** Builder and manager trust become full trust; container and access trust keep their narrower rights. */
    private static Map<UUID,Set<String>> trust(Claim claim){
        ArrayList<String> builders=new ArrayList<>(),containers=new ArrayList<>(),accessors=new ArrayList<>(),managers=new ArrayList<>();
        claim.getPermissions(builders,containers,accessors,managers);
        Map<UUID,Set<String>> trust=new HashMap<>();
        add(trust,builders,ALL);add(trust,managers,ALL);add(trust,containers,CONTAINER);add(trust,accessors,ACCESS);
        return trust;
    }
    private static void add(Map<UUID,Set<String>> trust,List<String> ids,Set<String> rights){
        for(String id:ids){
            // Public access ("public") and permission groups ("[group]") have no DyClaim equivalent.
            try{trust.computeIfAbsent(UUID.fromString(id),k->new HashSet<>()).addAll(rights);}catch(IllegalArgumentException ignored){}
        }
    }
}
