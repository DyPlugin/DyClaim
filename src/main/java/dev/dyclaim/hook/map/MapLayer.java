package dev.dyclaim.hook.map;

import java.util.List;

/** One web-map plugin. Implementations are created only when that plugin is enabled. */
public interface MapLayer {
    record Style(String label,double fillOpacity,double lineOpacity) {}
    /** Text shown for one area; built by the integration from the configured language. */
    interface Labels { String label(ClaimAreas.Area area); }
    String name();
    /** Draws every area from scratch, replacing whatever DyClaim showed on this map before. */
    void update(List<ClaimAreas.Area> areas,Style style,Labels labels);
    /** Applies only what changed since the last call: removed areas are taken away before added ones are drawn. */
    void change(List<ClaimAreas.Area> removed,List<ClaimAreas.Area> added,Style style,Labels labels);
    /** True when the map plugin restarted or dropped DyClaim's markers since the last update. */
    default boolean lost(){return false;}
    void close();
}
