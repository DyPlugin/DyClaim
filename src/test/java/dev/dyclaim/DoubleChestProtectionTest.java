package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class DoubleChestProtectionTest extends ManagerFixture {
    @Test void wildernessHalfCannotExposeClaimedHalf(){
        var claimed=chunk(0,0);var wild=chunk(1,0);Player owner=player("Owner",claimed),visitor=player("Visitor",wild);claims.claimChunk(owner,claimed);
        Inventory left=inventory(claimed),right=inventory(wild);DoubleChestInventory combined=mock(DoubleChestInventory.class);when(combined.getLeftSide()).thenReturn(left);when(combined.getRightSide()).thenReturn(right);
        InventoryOpenEvent event=mock(InventoryOpenEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getInventory()).thenReturn(combined);
        new ClaimProtectionListener(plugin).inventoryOpen(event);verify(event).setCancelled(true);
    }
    @Test void hopperCannotPullThroughWildernessHalf(){
        var claimed=chunk(0,0);var wild=chunk(1,0);claims.claimChunk(player("Owner",claimed),claimed);
        Inventory left=inventory(claimed),right=inventory(wild);DoubleChestInventory combined=mock(DoubleChestInventory.class);when(combined.getLeftSide()).thenReturn(left);when(combined.getRightSide()).thenReturn(right);
        InventoryMoveItemEvent event=mock(InventoryMoveItemEvent.class);when(event.getSource()).thenReturn(combined);when(event.getDestination()).thenReturn(right);
        new ClaimProtectionListener(plugin).hopper(event);verify(event).setCancelled(true);
    }
    private Inventory inventory(org.bukkit.Chunk chunk){Inventory inventory=mock(Inventory.class);Location location=mock(Location.class);when(location.getChunk()).thenReturn(chunk);when(inventory.getLocation()).thenReturn(location);return inventory;}
}
