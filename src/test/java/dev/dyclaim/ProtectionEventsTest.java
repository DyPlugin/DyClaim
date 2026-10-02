package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;

class ProtectionEventsTest extends ManagerFixture {
    @Test void defaultVillagerProtectionAppliesToOwnerAndPlayerProjectiles(){var chunk=chunk(0,0);Player owner=player("Owner",chunk);claims.claimChunk(owner,chunk);Villager villager=mock(Villager.class);Location location=owner.getLocation();when(villager.getLocation()).thenReturn(location);var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(villager);when(event.getDamager()).thenReturn(owner);var listener=new ClaimProtectionListener(plugin);listener.onEntityDamage(event);verify(event).setCancelled(true);reset(event);when(event.getEntity()).thenReturn(villager);Projectile arrow=mock(Projectile.class);when(arrow.getShooter()).thenReturn(owner);when(event.getDamager()).thenReturn(arrow);listener.onEntityDamage(event);verify(event).setCancelled(true);}
    @Test void enabledVillagerDamageStillRequiresEntityAccess(){var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);claims.getClaimAt(chunk).setChoice("villager-player-damage",true);Villager villager=mock(Villager.class);Location location=owner.getLocation();when(villager.getLocation()).thenReturn(location);var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(villager);when(event.getDamager()).thenReturn(visitor);new ClaimProtectionListener(plugin).onEntityDamage(event);verify(event).setCancelled(true);}
    @Test void doorRightDoesNotOpenContainers(){var chunk=chunk(0,0);Player owner=player("Owner",chunk),visitor=player("Visitor",chunk);claims.claimChunk(owner,chunk);var c=claims.getClaimAt(chunk);c.grant(visitor.getUniqueId(),0);c.getTrustGrants().get(visitor.getUniqueId()).permissions=new HashSet<>(Set.of("doors"));Block door=mock(Block.class);when(door.getChunk()).thenReturn(chunk);when(door.getType()).thenReturn(Material.OAK_DOOR);var event=mock(PlayerInteractEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);when(event.getClickedBlock()).thenReturn(door);var listener=new ClaimProtectionListener(plugin);listener.onPlayerInteract(event);verify(event,never()).setCancelled(true);when(door.getType()).thenReturn(Material.CHEST);listener.onPlayerInteract(event);verify(event).setCancelled(true);}
    @Test void externalHopperTransferIsDeniedInBothDirections(){var a=chunk(0,0);var b=chunk(1,0);Player owner=player("Owner",a);claims.claimChunk(owner,a);Inventory source=mock(Inventory.class),destination=mock(Inventory.class);Location from=mock(Location.class),to=mock(Location.class);when(from.getChunk()).thenReturn(a);when(to.getChunk()).thenReturn(b);when(source.getLocation()).thenReturn(from);when(destination.getLocation()).thenReturn(to);var event=mock(InventoryMoveItemEvent.class);when(event.getSource()).thenReturn(source);when(event.getDestination()).thenReturn(destination);var listener=new ClaimProtectionListener(plugin);listener.hopper(event);verify(event).setCancelled(true);clearInvocations(event);when(event.getSource()).thenReturn(destination);when(event.getDestination()).thenReturn(source);listener.hopper(event);verify(event).setCancelled(true);}
}
