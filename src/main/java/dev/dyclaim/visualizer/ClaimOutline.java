package dev.dyclaim.visualizer;

import java.util.*;
import java.util.function.Predicate;

/** Metadata-only local traversal: no world/chunk loads, no artificial clipped boundaries. */
public final class ClaimOutline {
    public record Cell(int x,int z) {}
    public enum Side { NORTH(0,-1),SOUTH(0,1),WEST(-1,0),EAST(1,0);
        final int dx,dz;Side(int dx,int dz){this.dx=dx;this.dz=dz;}
    }
    public record Edge(Cell cell,Side side) {}
    private ClaimOutline(){}
    public static List<Edge> single(Cell cell){return Arrays.stream(Side.values()).map(side->new Edge(cell,side)).toList();}
    public static List<Edge> connected(Cell origin,int radius,Predicate<Cell> sameOwner){
        int limit=Math.max(0,Math.min(8,radius));
        Set<Cell> visited=new HashSet<>();Deque<Cell> queue=new ArrayDeque<>();List<Edge> result=new ArrayList<>();
        visited.add(origin);queue.add(origin);
        while(!queue.isEmpty()){
            Cell c=queue.remove();
            for(Side side:Side.values()){
                Cell next=new Cell(c.x+side.dx,c.z+side.dz);
                if(!sameOwner.test(next)){result.add(new Edge(c,side));continue;}
                if(Math.abs((long)next.x-origin.x)<=limit&&Math.abs((long)next.z-origin.z)<=limit&&visited.add(next))queue.add(next);
            }
        }
        return List.copyOf(result);
    }
}
