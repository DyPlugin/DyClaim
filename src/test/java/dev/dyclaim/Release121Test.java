package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Release121Test extends ManagerFixture {
    private Block block(org.bukkit.Chunk chunk,Material type){Block b=mock(Block.class);TestBlocks.at(b,chunk);when(b.getType()).thenReturn(type);return b;}
    private PlayerInteractEvent click(Player player,Block block){
        var event=mock(PlayerInteractEvent.class);when(event.getPlayer()).thenReturn(player);when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);when(event.getClickedBlock()).thenReturn(block);return event;
    }
    private InventoryOpenEvent open(Player player,org.bukkit.Chunk chunk,Material type){
        Block b=block(chunk,type);Location location=mock(Location.class);TestBlocks.at(location,chunk);when(location.getBlock()).thenReturn(b);
        Inventory inventory=mock(Inventory.class);when(inventory.getLocation()).thenReturn(location);
        InventoryOpenEvent event=mock(InventoryOpenEvent.class);when(event.getPlayer()).thenReturn(player);when(event.getInventory()).thenReturn(inventory);return event;
    }
    @Test void visitorContainersIsOffByDefaultAndOwnersCanTurnItOn(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);ClaimData c=claims.getClaimAt(chunk);
        var listener=new ClaimProtectionListener(plugin);
        var closed=click(visitor,block(chunk,Material.CHEST));listener.onPlayerInteract(closed);verify(closed).setCancelled(true);
        features.handle(owner,new String[]{"containers"});
        assertTrue(access.enabled(c,"visitor-containers"));
        var chest=click(visitor,block(chunk,Material.CHEST));listener.onPlayerInteract(chest);verify(chest,never()).setCancelled(true);
        var opened=open(visitor,chunk,Material.BARREL);listener.inventoryOpen(opened);verify(opened,never()).setCancelled(true);
        features.handle(owner,new String[]{"sandıklar"});
        assertFalse(access.enabled(c,"visitor-containers"),"the Turkish word toggles it back off");
    }
    @Test void visitorContainersOpensOnlyStorageBlocks(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);
        claims.getClaimAt(chunk).setChoice("visitor-containers",true);var listener=new ClaimProtectionListener(plugin);
        for(Material type:new Material[]{Material.CHEST,Material.TRAPPED_CHEST,Material.BARREL,Material.SHULKER_BOX,Material.RED_SHULKER_BOX,Material.FURNACE,Material.BLAST_FURNACE,Material.SMOKER,Material.HOPPER,Material.DISPENSER,Material.DROPPER,Material.BREWING_STAND}){
            var event=click(visitor,block(chunk,type));listener.onPlayerInteract(event);verify(event,never()).setCancelled(true);
        }
        for(Material type:new Material[]{Material.ANVIL,Material.JUKEBOX,Material.LECTERN,Material.RED_BED,Material.BEACON,Material.ENCHANTING_TABLE,Material.CHISELED_BOOKSHELF,Material.DECORATED_POT}){
            var event=click(visitor,block(chunk,type));listener.onPlayerInteract(event);verify(event).setCancelled(true);
        }
        var notStorage=open(visitor,chunk,Material.RAIL);listener.inventoryOpen(notStorage);verify(notStorage).setCancelled(true);
    }
    @Test void visitorsStillCannotBreakContainers(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);
        claims.getClaimAt(chunk).setChoice("visitor-containers",true);
        var event=mock(BlockBreakEvent.class);Block chest=block(chunk,Material.CHEST);when(event.getPlayer()).thenReturn(visitor);when(event.getBlock()).thenReturn(chest);
        new ClaimProtectionListener(plugin).onBlockBreak(event);verify(event).setCancelled(true);
    }
    @Test void serverCanForceOrLockVisitorContainers(){
        var chunk=chunk(0,0);Player owner=player("Owner",chunk);claims.claimChunk(owner,chunk);ClaimData c=claims.getClaimAt(chunk);
        values.set("protection.visitor-containers.default",true);assertTrue(access.enabled(c,"visitor-containers"));
        values.set("protection.visitor-containers.player-toggle",false);
        features.handle(owner,new String[]{"containers"});verify(owner).sendMessage("setting-locked");assertTrue(access.enabled(c,"visitor-containers"));
        values.set("protection.visitor-containers.forced-value",false);assertFalse(access.enabled(c,"visitor-containers"));
    }
}
