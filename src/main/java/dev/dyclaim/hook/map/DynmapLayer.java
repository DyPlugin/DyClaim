package dev.dyclaim.hook.map;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import java.util.*;

/** Dynmap's marker API is main-thread only; DyClaim refreshes it from the global scheduler. */
public final class DynmapLayer implements MapLayer {
    private static final String SET="dyclaim.claims";
    private volatile MarkerAPI markers;
    private volatile boolean fresh;
    private final DynmapCommonAPIListener listener=new DynmapCommonAPIListener(){
        @Override public void apiEnabled(DynmapCommonAPI api){markers=api.markerAPIInitialized()?api.getMarkerAPI():null;fresh=true;}
        @Override public void apiDisabled(DynmapCommonAPI api){markers=null;}
    };
    public DynmapLayer(){DynmapCommonAPIListener.register(listener);}
    public String name(){return "Dynmap";}
    public boolean lost(){return fresh;}
    public void update(List<ClaimAreas.Area> areas,Style style,Labels labels){
        MarkerAPI api=markers;if(api==null)return;
        MarkerSet set=api.getMarkerSet(SET);
        if(set==null)set=api.createMarkerSet(SET,style.label(),null,false);
        if(set==null)return;
        fresh=false;
        set.setMarkerSetLabel(style.label());
        for(AreaMarker old:new ArrayList<>(set.getAreaMarkers()))old.deleteMarker();
        for(ClaimAreas.Area area:areas)create(set,area,style,labels);
    }
    public void change(List<ClaimAreas.Area> removed,List<ClaimAreas.Area> added,Style style,Labels labels){
        MarkerAPI api=markers;if(api==null)return;
        MarkerSet set=api.getMarkerSet(SET);
        if(set==null){fresh=true;return;}
        for(ClaimAreas.Area area:removed){AreaMarker old=set.findAreaMarker(area.id());if(old!=null)old.deleteMarker();}
        for(ClaimAreas.Area area:added)create(set,area,style,labels);
    }
    private static void create(MarkerSet set,ClaimAreas.Area area,Style style,Labels labels){
        double[] x={area.minBlockX(),area.maxBlockX(),area.maxBlockX(),area.minBlockX()};
        double[] z={area.minBlockZ(),area.minBlockZ(),area.maxBlockZ(),area.maxBlockZ()};
        AreaMarker marker=set.createAreaMarker(area.id(),labels.label(area),false,area.world(),x,z,false);
        if(marker==null)return;
        int rgb=ClaimAreas.color(area.owner()).getRGB()&0xffffff;
        marker.setFillStyle(style.fillOpacity(),rgb);marker.setLineStyle(2,style.lineOpacity(),rgb);
    }
    public void close(){
        MarkerAPI api=markers;if(api!=null){MarkerSet set=api.getMarkerSet(SET);if(set!=null)set.deleteMarkerSet();}
        DynmapCommonAPIListener.unregister(listener);markers=null;
    }
}
