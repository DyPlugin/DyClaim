package dev.dyclaim.hook;

import dev.dyclaim.manager.ClaimImporter;
import java.lang.reflect.Method;
import java.util.*;

/** Reads ClaimChunk through its public handlers by reflection, so DyClaim needs no ClaimChunk dependency. */
public final class ClaimChunkImport implements ClaimImporter.Source {
    private final Object claimChunk;
    /** Takes the ClaimChunk plugin instance; typed as Object so this class never links against ClaimChunk. */
    public ClaimChunkImport(Object claimChunk){this.claimChunk=claimChunk;}
    public String id(){return "claimchunk";}
    public int read(List<ClaimImporter.Candidate> out){
        try{
            Object chunks=claimChunk.getClass().getMethod("getChunkHandler").invoke(claimChunk);
            Object players=claimChunk.getClass().getMethod("getPlayerHandler").invoke(claimChunk);
            Method claimed=chunks.getClass().getMethod("getClaimedChunks",UUID.class);
            Method permissions=players.getClass().getMethod("getAllPlayerPermissions",claimed.getReturnType().getComponentType());
            int count=0;
            for(Object data:(Collection<?>)players.getClass().getMethod("getJoinedPlayers").invoke(players)){
                UUID owner=(UUID)data.getClass().getField("player").get(data);
                String name=ClaimImporter.nameOf(owner,(String)data.getClass().getField("lastIgn").get(data));
                for(Object pos:(Object[])claimed.invoke(chunks,owner)){
                    String world=(String)pos.getClass().getMethod("world").invoke(pos);
                    int x=(int)pos.getClass().getMethod("x").invoke(pos),z=(int)pos.getClass().getMethod("z").invoke(pos);
                    out.add(new ClaimImporter.Candidate(owner,name,world,x,z,trust((Map<?,?>)permissions.invoke(players,pos))));count++;
                }
            }
            return count;
        }catch(ReflectiveOperationException|ClassCastException ex){
            throw new IllegalStateException("Unsupported ClaimChunk version: "+ex,ex);
        }
    }
    /** ClaimChunk flag names mapped to the closest DyClaim trust rights. */
    private static Map<UUID,Set<String>> trust(Map<?,?> access){
        Map<UUID,Set<String>> trust=new HashMap<>();
        if(access==null)return trust;
        access.forEach((player,flags)->{
            if(!(player instanceof UUID uuid)||!(flags instanceof Map<?,?> map))return;
            Set<String> rights=new HashSet<>();
            map.forEach((flag,allowed)->{
                if(!Boolean.TRUE.equals(allowed))return;
                switch(String.valueOf(flag)){
                    case "break","place"->rights.add("build");
                    case "doors"->{rights.add("doors");rights.add("trapdoors");}
                    case "redstone"->rights.add("redstone");
                    case "useContainers"->rights.add("containers");
                    case "interactEntities","interactVehicles"->rights.add("entities");
                    default->{}
                }
            });
            if(!rights.isEmpty())trust.put(uuid,rights);
        });
        return trust;
    }
}
