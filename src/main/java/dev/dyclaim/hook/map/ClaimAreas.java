package dev.dyclaim.hook.map;

import dev.dyclaim.model.ClaimData;
import java.util.*;

/**
 * Turns chunk claims into few rectangles for web maps: each owner's chunks in a world are
 * joined into horizontal runs, and identical runs on neighbouring rows are stacked.
 */
public final class ClaimAreas {
    /** Inclusive chunk bounds of one rectangle owned by one player. */
    public record Area(String world,UUID owner,String ownerName,int minX,int minZ,int maxX,int maxZ,List<String> names) {
        public int minBlockX(){return minX<<4;}
        public int minBlockZ(){return minZ<<4;}
        public int maxBlockX(){return (maxX+1)<<4;}
        public int maxBlockZ(){return (maxZ+1)<<4;}
        public String id(){return "dyclaim_"+world+"_"+minX+"_"+minZ+"_"+maxX+"_"+maxZ;}
    }
    private ClaimAreas() {}

    public static List<Area> of(Collection<ClaimData> claims,Set<String> hiddenWorlds){
        Map<String,Map<UUID,List<ClaimData>>> grouped=new TreeMap<>();
        for(ClaimData claim:claims){
            if(hiddenWorlds.contains(claim.getWorld()))continue;
            grouped.computeIfAbsent(claim.getWorld(),w->new LinkedHashMap<>()).computeIfAbsent(claim.getOwnerUUID(),u->new ArrayList<>()).add(claim);
        }
        List<Area> out=new ArrayList<>();
        grouped.forEach((world,owners)->owners.forEach((owner,list)->out.addAll(merge(world,owner,list))));
        return out;
    }

    private static List<Area> merge(String world,UUID owner,List<ClaimData> claims){
        String ownerName=claims.get(0).getOwnerName();
        Map<Long,String> names=new HashMap<>();
        TreeMap<Integer,TreeSet<Integer>> rows=new TreeMap<>();
        for(ClaimData claim:claims){
            rows.computeIfAbsent(claim.getChunkZ(),z->new TreeSet<>()).add(claim.getChunkX());
            if(claim.getName()!=null)names.put(pack(claim.getChunkX(),claim.getChunkZ()),claim.getName());
        }
        // Runs of consecutive x in one row, as [minX, maxX].
        List<Area> open=new ArrayList<>(),done=new ArrayList<>();
        Integer previousZ=null;
        for(var row:rows.entrySet()){
            int z=row.getKey();
            List<int[]> runs=new ArrayList<>();int start=Integer.MIN_VALUE,end=Integer.MIN_VALUE;
            for(int x:row.getValue()){
                if(start!=Integer.MIN_VALUE&&x==end+1){end=x;continue;}
                if(start!=Integer.MIN_VALUE)runs.add(new int[]{start,end});
                start=x;end=x;
            }
            runs.add(new int[]{start,end});
            List<Area> next=new ArrayList<>();
            for(int[] run:runs){
                Area extended=null;
                if(previousZ!=null&&previousZ==z-1)for(Area area:open)if(area.minX()==run[0]&&area.maxX()==run[1]){extended=area;break;}
                if(extended!=null){open.remove(extended);next.add(new Area(world,owner,ownerName,run[0],extended.minZ(),run[1],z,List.of()));}
                else next.add(new Area(world,owner,ownerName,run[0],z,run[1],z,List.of()));
            }
            done.addAll(open);open=next;previousZ=z;
        }
        done.addAll(open);
        List<Area> named=new ArrayList<>();
        for(Area area:done){
            TreeSet<String> inside=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(int x=area.minX();x<=area.maxX();x++)for(int z=area.minZ();z<=area.maxZ();z++){String name=names.get(pack(x,z));if(name!=null)inside.add(name);}
            named.add(new Area(world,owner,ownerName,area.minX(),area.minZ(),area.maxX(),area.maxZ(),List.copyOf(inside)));
        }
        return named;
    }
    private static long pack(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}

    /** Stable, readable colour per owner so the same player keeps one colour on every map. */
    public static java.awt.Color color(UUID owner){
        float hue=(owner.hashCode()&0xffff)/65536f;
        return java.awt.Color.getHSBColor(hue,0.65f,0.9f);
    }
}
