package dev.dyclaim.hook;

import dev.dyclaim.DyClaim;
import dev.dyclaim.hook.map.ClaimAreas;
import dev.dyclaim.hook.map.MapLayer;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Optional PlaceholderAPI and web-map hooks. Each is started only when its plugin and its switch are on. */
public final class Integrations {
    private final DyClaim plugin;
    private Object placeholders;
    private final List<MapLayer> maps=new ArrayList<>();
    /** What a layer last drew; later refreshes send it only the difference, so large servers do not redraw every marker. */
    private record Shown(Set<ClaimAreas.Area> areas,List<Object> look) {}
    private final Map<MapLayer,Shown> shown=new IdentityHashMap<>();
    /** Everything one refresh needs; worked out off the server thread, then applied on it. */
    private record Plan(List<MapLayer> layers,List<ClaimAreas.Area> areas,MapLayer.Style style,MapLayer.Labels labels,Shown next,Map<MapLayer,Shown> before,Map<Shown,List<List<ClaimAreas.Area>>> differences) {}
    private final java.util.concurrent.atomic.AtomicBoolean refreshing=new java.util.concurrent.atomic.AtomicBoolean();
    private BukkitTask refresh;

    public Integrations(DyClaim plugin){this.plugin=plugin;}

    public synchronized void start(){
        var values=plugin.getConfigManager().values();
        if(values.getBoolean("integrations.placeholderapi.enabled")&&enabled("PlaceholderAPI")&&placeholders==null){
            try{placeholders=PlaceholderHook.register(plugin);if(placeholders!=null)plugin.getLogger().info("PlaceholderAPI placeholders registered (%dyclaim_...%).");}
            catch(LinkageError|RuntimeException ex){plugin.getLogger().warning("PlaceholderAPI hook failed: "+ex);}
        }
        if(!values.getBoolean("integrations.maps.enabled"))return;
        // Dynmap marker calls must run on the main thread; it does not support Folia anyway.
        addMap("BlueMap","bluemap","BlueMapLayer");
        if(!plugin.getPlatformTasks().isRegionThreaded())addMap("dynmap","dynmap","DynmapLayer");
        if(!plugin.getPlatformTasks().isRegionThreaded())addMap("squaremap","squaremap","SquaremapLayer");
        if(maps.isEmpty())return;
        long ticks=values.getLong("integrations.maps.refresh-seconds")*20L;
        refresh=plugin.getPlatformTasks().globalTimer(this::refreshInBackground,100L,ticks);
    }
    /** Layers are loaded by name so a missing map plugin can never break DyClaim class loading. */
    private void addMap(String pluginName,String switchName,String layerClass){
        if(!plugin.getConfigManager().values().getBoolean("integrations.maps."+switchName)||!enabled(pluginName))return;
        try{MapLayer layer=(MapLayer)Class.forName("dev.dyclaim.hook.map."+layerClass).getDeclaredConstructor().newInstance();maps.add(layer);plugin.getLogger().info(layer.name()+" claim layer enabled.");}
        catch(LinkageError|ReflectiveOperationException|RuntimeException ex){plugin.getLogger().warning(pluginName+" hook failed: "+ex);}
    }
    private boolean enabled(String name){Plugin other=plugin.getServer().getPluginManager().getPlugin(name);return other!=null&&other.isEnabled();}

    /** Refreshes the maps right away on the calling thread. */
    public void refreshMaps(){Plan plan=plan();if(plan!=null)apply(plan);}
    /** Claim areas, labels and differences are worked out off the server thread; only map plugin calls run on it. */
    private void refreshInBackground(){
        if(!refreshing.compareAndSet(false,true))return;
        try{plugin.getPlatformTasks().async(()->{
            try{
                Plan plan=plan();
                if(plan==null){refreshing.set(false);return;}
                plugin.getPlatformTasks().globalNow(()->{try{apply(plan);}finally{refreshing.set(false);}});
            }catch(RuntimeException ex){refreshing.set(false);plugin.getLogger().warning("Map refresh failed: "+ex);}
        });}catch(RuntimeException ex){refreshing.set(false);}
    }
    private Plan plan(){
        List<MapLayer> current;Map<MapLayer,Shown> before=new IdentityHashMap<>();
        synchronized(this){current=List.copyOf(maps);for(MapLayer layer:current)before.put(layer,shown.get(layer));}
        if(current.isEmpty())return null;
        var values=plugin.getConfigManager().values();
        var areas=ClaimAreas.of(plugin.getClaimManager().getAllClaims().values(),new HashSet<>(values.getStringList("integrations.maps.hidden-worlds")));
        var style=new MapLayer.Style(values.getString("integrations.maps.layer-name","Claims"),values.getDouble("integrations.maps.fill-opacity"),values.getDouble("integrations.maps.line-opacity"));
        // Labels are built once per refresh from the two templates and shared by every map plugin.
        String single=String.valueOf(plugin.getMessageManager().getMessage("map-label",Map.of()));
        String named=String.valueOf(plugin.getMessageManager().getMessage("map-label-named",Map.of()));
        Map<ClaimAreas.Area,String> built=new IdentityHashMap<>();
        for(ClaimAreas.Area area:areas){
            String names=String.join(", ",area.names());
            String text=(names.isEmpty()?single:named).replace("{owner}",area.ownerName()).replace("{names}",names);
            built.put(area,ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',text)));
        }
        // Label templates can change with the language or message files, so they are part of how a map looks.
        Shown next=new Shown(new HashSet<>(areas),List.of(style,single,named));
        Map<Shown,List<List<ClaimAreas.Area>>> differences=new IdentityHashMap<>();
        for(Shown old:before.values())if(old!=null&&old.look().equals(next.look()))differences.computeIfAbsent(old,b->List.of(
                b.areas().stream().filter(a->!next.areas().contains(a)).toList(),areas.stream().filter(a->!b.areas().contains(a)).toList()));
        return new Plan(current,areas,style,built::get,next,before,differences);
    }
    private void apply(Plan plan){
        for(MapLayer layer:plan.layers()){
            Shown before;synchronized(this){if(!maps.contains(layer))continue;before=shown.get(layer);}
            try{
                var difference=before==plan.before().get(layer)?plan.differences().get(before):null;
                if(difference==null||layer.lost())layer.update(plan.areas(),plan.style(),plan.labels());
                else if(!difference.get(0).isEmpty()||!difference.get(1).isEmpty())layer.change(difference.get(0),difference.get(1),plan.style(),plan.labels());
                synchronized(this){if(maps.contains(layer))shown.put(layer,plan.next());}
            }catch(LinkageError|RuntimeException ex){synchronized(this){shown.remove(layer);}plugin.getLogger().warning(layer.name()+" claim layer update failed: "+ex);}
        }
    }
    public synchronized boolean placeholdersActive(){return placeholders!=null;}
    public synchronized List<String> activeMaps(){return maps.stream().map(MapLayer::name).toList();}

    public synchronized void close(){
        if(refresh!=null){refresh.cancel();refresh=null;}
        for(MapLayer layer:maps){try{layer.close();}catch(LinkageError|RuntimeException ignored){}}
        maps.clear();shown.clear();refreshing.set(false);
        if(placeholders!=null){try{PlaceholderHook.unregister(placeholders);}catch(LinkageError|RuntimeException ignored){}}
        placeholders=null;
    }
    /** Reload applies changed switches by stopping and starting the hooks again. */
    public void reload(){close();start();}
}
