package dev.dyclaim.hook.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import java.util.*;

/** One layer per enabled squaremap world; squaremap reads the markers from its own thread. */
public final class SquaremapLayer implements MapLayer {
    private static final Key KEY=Key.of("dyclaim_claims");
    private final Map<String,SimpleLayerProvider> layers=new HashMap<>();
    private final Map<String,MapWorld> worlds=new HashMap<>();
    public String name(){return "squaremap";}
    public synchronized void update(List<ClaimAreas.Area> areas,Style style,Labels labels){
        Squaremap api=SquaremapProvider.get();
        Map<String,List<ClaimAreas.Area>> byWorld=new HashMap<>();
        for(ClaimAreas.Area area:areas)byWorld.computeIfAbsent(area.world(),w->new ArrayList<>()).add(area);
        for(World world:Bukkit.getWorlds()){
            Optional<MapWorld> mapWorld=api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world));
            if(mapWorld.isEmpty())continue;
            SimpleLayerProvider layer=layers.computeIfAbsent(world.getName(),name->{
                SimpleLayerProvider created=SimpleLayerProvider.builder(style.label()).showControls(true).defaultHidden(false).layerPriority(5).zIndex(250).build();
                if(!mapWorld.get().layerRegistry().hasEntry(KEY))mapWorld.get().layerRegistry().register(KEY,created);
                worlds.put(name,mapWorld.get());return created;
            });
            layer.clearMarkers();
            for(ClaimAreas.Area area:byWorld.getOrDefault(world.getName(),List.of()))add(layer,area,style,labels);
        }
    }
    public synchronized void change(List<ClaimAreas.Area> removed,List<ClaimAreas.Area> added,Style style,Labels labels){
        for(ClaimAreas.Area area:removed){SimpleLayerProvider layer=layers.get(area.world());if(layer!=null)layer.removeMarker(key(area));}
        for(ClaimAreas.Area area:added){SimpleLayerProvider layer=layers.get(area.world());if(layer!=null)add(layer,area,style,labels);}
    }
    private static void add(SimpleLayerProvider layer,ClaimAreas.Area area,Style style,Labels labels){
        java.awt.Color color=ClaimAreas.color(area.owner());String label=labels.label(area);
        layer.addMarker(key(area),Marker.rectangle(Point.of(area.minBlockX(),area.minBlockZ()),Point.of(area.maxBlockX(),area.maxBlockZ()))
                .markerOptions(MarkerOptions.builder().fillColor(color).fillOpacity(style.fillOpacity()).strokeColor(color).strokeOpacity(style.lineOpacity()).strokeWeight(2).hoverTooltip(label)));
    }
    private static Key key(ClaimAreas.Area area){return Key.of(area.id().replaceAll("[^a-zA-Z0-9_]","_"));}
    public synchronized void close(){
        worlds.forEach((name,world)->{try{if(world.layerRegistry().hasEntry(KEY))world.layerRegistry().unregister(KEY);}catch(RuntimeException ignored){}});
        worlds.clear();layers.clear();
    }
}
