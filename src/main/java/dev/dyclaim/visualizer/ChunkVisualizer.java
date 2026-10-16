package dev.dyclaim.visualizer;
import dev.dyclaim.DyClaim;
import dev.dyclaim.platform.*;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ChunkVisualizer {
    private final RegionalBorderView borders;
    public ChunkVisualizer(DyClaim plugin){var tasks=plugin.getPlatformTasks();borders=new RegionalBorderView(plugin,tasks==null?PlatformTasks.bukkit(plugin):tasks,this::bedrock);}
    public boolean isViewing(UUID uuid){return borders.isViewing(uuid);}
    public void showChunkBorders(Player player){showChunkBorders(player,player.getLocation().getChunk());}
    public void showChunkBorders(Player player,Chunk chunk){borders.show(player,chunk);}
    public void cleanupPlayer(Player player){borders.hide(player);}
    public void cleanupAll(){borders.reset();}
    private boolean bedrock(Player player){try{Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi");Object instance=api.getMethod("getInstance").invoke(null);return (boolean)api.getMethod("isFloodgatePlayer",UUID.class).invoke(instance,player.getUniqueId());}catch(ReflectiveOperationException|LinkageError ex){return false;}}
}
