package dev.dyclaim.hook.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import java.util.*;
import java.util.function.Consumer;

/** BlueMap keeps markers in memory only; after BlueMap reloads, lost() makes DyClaim draw them again. */
public final class BlueMapLayer implements MapLayer {
    private static final String SET="dyclaim";
    private volatile BlueMapAPI api;
    private volatile boolean fresh;
    private final Consumer<BlueMapAPI> enable=value->{api=value;fresh=true;},disable=value->api=null;
    public BlueMapLayer(){BlueMapAPI.onEnable(enable);BlueMapAPI.onDisable(disable);}
    public String name(){return "BlueMap";}
    public boolean lost(){return fresh;}
    public void update(List<ClaimAreas.Area> areas,Style style,Labels labels){
        BlueMapAPI current=api;if(current==null)return;
        fresh=false;
        Map<String,List<ClaimAreas.Area>> byWorld=new HashMap<>();
        for(ClaimAreas.Area area:areas)byWorld.computeIfAbsent(area.world(),w->new ArrayList<>()).add(area);
        Set<BlueMapMap> touched=new HashSet<>();
        byWorld.forEach((worldName,list)->forMaps(current,worldName,map->{fill(map,list,style,labels);touched.add(map);}));
        // Maps of worlds that lost their last claim keep an empty, toggleable layer.
        for(BlueMapMap map:current.getMaps())if(!touched.contains(map))fill(map,List.of(),style,labels);
    }
    public void change(List<ClaimAreas.Area> removed,List<ClaimAreas.Area> added,Style style,Labels labels){
        BlueMapAPI current=api;if(current==null)return;
        for(ClaimAreas.Area area:removed)forMaps(current,area.world(),map->{MarkerSet set=map.getMarkerSets().get(SET);if(set!=null)set.remove(area.id());});
        for(ClaimAreas.Area area:added)forMaps(current,area.world(),map->{
            MarkerSet set=map.getMarkerSets().get(SET);
            if(set==null){fresh=true;return;}
            set.put(area.id(),marker(area,style,labels));
        });
    }
    private static void forMaps(BlueMapAPI api,String worldName,Consumer<BlueMapMap> action){
        World world=Bukkit.getWorld(worldName);if(world==null)return;
        api.getWorld(world).ifPresent(bmWorld->bmWorld.getMaps().forEach(action));
    }
    private void fill(BlueMapMap map,List<ClaimAreas.Area> areas,Style style,Labels labels){
        MarkerSet set=MarkerSet.builder().label(style.label()).toggleable(true).defaultHidden(false).build();
        for(ClaimAreas.Area area:areas)set.put(area.id(),marker(area,style,labels));
        map.getMarkerSets().put(SET,set);
    }
    private static ShapeMarker marker(ClaimAreas.Area area,Style style,Labels labels){
        java.awt.Color color=ClaimAreas.color(area.owner());String label=labels.label(area);
        return ShapeMarker.builder().label(label).detail(label)
                .shape(Shape.createRect(area.minBlockX(),area.minBlockZ(),area.maxBlockX(),area.maxBlockZ()),64f).depthTestEnabled(false)
                .fillColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),(float)style.fillOpacity()))
                .lineColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),(float)style.lineOpacity())).lineWidth(2).build();
    }
    public void close(){
        BlueMapAPI current=api;if(current!=null)for(BlueMapMap map:current.getMaps())map.getMarkerSets().remove(SET);
        BlueMapAPI.unregisterListener(enable);BlueMapAPI.unregisterListener(disable);api=null;
    }
}
