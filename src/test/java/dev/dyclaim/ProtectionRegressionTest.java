package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.potion.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectionRegressionTest extends ManagerFixture {
    private ClaimProtectionListener listener(){return new ClaimProtectionListener(plugin);}
    @Test void distantDispenserArrowsCannotDamageAnimalsOrBypassDisabledPvp() {
        Chunk inside=chunk(0,0),outside=chunk(4,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);
        Block dispenser=mock(Block.class);when(dispenser.getChunk()).thenReturn(outside);
        org.bukkit.projectiles.BlockProjectileSource source=mock(org.bukkit.projectiles.BlockProjectileSource.class);when(source.getBlock()).thenReturn(dispenser);
        Projectile arrow=mock(Projectile.class);when(arrow.getShooter()).thenReturn(source);
        Cow cow=mock(Cow.class);when(cow.getLocation()).thenAnswer(i->owner.getLocation());
        EntityDamageByEntityEvent event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(cow);when(event.getDamager()).thenReturn(arrow);
        listener().onEntityDamage(event);verify(event).setCancelled(true);
        clearInvocations(event);when(event.getEntity()).thenReturn(owner);listener().onEntityDamageByEntity(event);verify(event).setCancelled(true);
        claims.getClaimAt(inside).setChoice("pvp",true);clearInvocations(event);
        listener().onEntityDamageByEntity(event);verify(event,never()).setCancelled(true);
    }
    @Test void disabledExplosionsProtectDecorationsAndAnimalsFromOwnerlessTnt() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);
        Cow cow=mock(Cow.class);when(cow.getLocation()).thenAnswer(i->owner.getLocation());
        EntityDamageEvent event=mock(EntityDamageEvent.class);when(event.getEntity()).thenReturn(cow);when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
        listener().explosionEntityDamage(event);verify(event).setCancelled(true);
        claims.getClaimAt(inside).setChoice("explosions",true);clearInvocations(event);
        listener().explosionEntityDamage(event);verify(event,never()).setCancelled(true);
    }
    @Test void dispenserCannotEmptyBucketAcrossClaimBoundaryButSameOwnerAutomationWorks() {
        Chunk outside=chunk(0,0),inside=chunk(1,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);
        Block source=mock(Block.class),target=mock(Block.class);when(source.getChunk()).thenReturn(outside);when(target.getChunk()).thenReturn(inside);
        org.bukkit.block.data.Directional facing=mock(org.bukkit.block.data.Directional.class);when(facing.getFacing()).thenReturn(BlockFace.EAST);when(source.getBlockData()).thenReturn(facing);when(source.getRelative(BlockFace.EAST)).thenReturn(target);
        BlockDispenseEvent event=mock(BlockDispenseEvent.class);when(event.getBlock()).thenReturn(source);
        listener().dispense(event);verify(event).setCancelled(true);
        claims.claimChunk(owner,outside);clearInvocations(event);listener().dispense(event);verify(event,never()).setCancelled(true);
    }
    @Test void outsideTreeCannotReplaceBlocksInAClaimButItsOtherBlocksRemain() {
        Chunk outside=chunk(0,0),inside=chunk(1,0);Player owner=player("Owner",inside);claims.claimChunk(owner,inside);
        Block a=mock(Block.class),b=mock(Block.class);when(a.getChunk()).thenReturn(outside);when(b.getChunk()).thenReturn(inside);
        BlockState wild=mock(BlockState.class),protectedBlock=mock(BlockState.class);when(wild.getBlock()).thenReturn(a);when(protectedBlock.getBlock()).thenReturn(b);
        Location origin=mock(Location.class);when(origin.getChunk()).thenReturn(outside);
        StructureGrowEvent event=mock(StructureGrowEvent.class);when(event.getLocation()).thenReturn(origin);
        List<BlockState> changes=new ArrayList<>(List.of(wild,protectedBlock));when(event.getBlocks()).thenReturn(changes);
        listener().structureGrow(event);assertEquals(List.of(wild),changes);
        claims.claimChunk(owner,outside);changes.add(protectedBlock);listener().structureGrow(event);assertEquals(2,changes.size());
    }
    @Test void visitorsCannotStripLogsEvenWithContainerTrust() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        var claim=claims.getClaimAt(inside);claim.grant(visitor.getUniqueId(),0);claim.getTrustGrants().get(visitor.getUniqueId()).permissions=new HashSet<>(Set.of("containers"));
        Block block=mock(Block.class);when(block.getChunk()).thenReturn(inside);when(block.getType()).thenReturn(Material.OAK_LOG);
        org.bukkit.inventory.ItemStack axe=mock(org.bukkit.inventory.ItemStack.class);when(axe.getType()).thenReturn(Material.IRON_AXE);
        PlayerInteractEvent event=mock(PlayerInteractEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getClickedBlock()).thenReturn(block);when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);when(event.getItem()).thenReturn(axe);
        listener().onPlayerInteract(event);verify(event).setCancelled(true);
        when(block.getType()).thenReturn(Material.CHEST);clearInvocations(event);
        listener().onPlayerInteract(event);verify(event,never()).setCancelled(true);
        when(block.getType()).thenReturn(Material.OAK_LOG);
        claim.getTrustGrants().get(visitor.getUniqueId()).permissions.add("build");clearInvocations(event);
        listener().onPlayerInteract(event);verify(event,never()).setCancelled(true);
    }
    @Test void editingAnExistingSignRequiresBuildAccess() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        Block block=mock(Block.class);when(block.getChunk()).thenReturn(inside);
        SignChangeEvent event=mock(SignChangeEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getBlock()).thenReturn(block);
        listener().signChange(event);verify(event).setCancelled(true);
    }
    @Test void bedPlacedOutsideCannotReplaceBlockInsideAnotherClaim() {
        Chunk outside=chunk(0,0),inside=chunk(1,0);Player owner=player("Owner",inside),visitor=player("Visitor",outside);
        claims.claimChunk(owner,inside);
        Block outsideBlock=mock(Block.class),insideBlock=mock(Block.class);when(outsideBlock.getChunk()).thenReturn(outside);when(insideBlock.getChunk()).thenReturn(inside);
        BlockState a=mock(BlockState.class),b=mock(BlockState.class);when(a.getBlock()).thenReturn(outsideBlock);when(b.getBlock()).thenReturn(insideBlock);
        BlockMultiPlaceEvent event=mock(BlockMultiPlaceEvent.class);when(event.getPlayer()).thenReturn(visitor);when(event.getBlock()).thenReturn(outsideBlock);when(event.getReplacedBlockStates()).thenReturn(List.of(a,b));
        listener().onBlockPlace(event);verify(event).setCancelled(true);
        clearInvocations(event);claims.getClaimAt(inside).grant(visitor.getUniqueId(),0);listener().onBlockPlace(event);verify(event,never()).setCancelled(true);
    }
    @Test void fireChargePlayerIgnitionRequiresBuildAccess() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        Block block=mock(Block.class);when(block.getChunk()).thenReturn(inside);
        BlockIgniteEvent event=mock(BlockIgniteEvent.class);when(event.getBlock()).thenReturn(block);when(event.getPlayer()).thenReturn(visitor);when(event.getCause()).thenReturn(BlockIgniteEvent.IgniteCause.FIREBALL);
        listener().onBlockIgnite(event);verify(event).setCancelled(true);
    }
    @Test void projectileCannotDestroyAnotherPlayersVehicle() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        Vehicle boat=mock(Vehicle.class);when(boat.getLocation()).thenAnswer(i->owner.getLocation());
        Projectile arrow=mock(Projectile.class);when(arrow.getShooter()).thenReturn(visitor);
        VehicleDestroyEvent event=mock(VehicleDestroyEvent.class);when(event.getVehicle()).thenReturn(boat);when(event.getAttacker()).thenReturn(arrow);
        listener().onVehicleDestroy(event);verify(event).setCancelled(true);
    }
    @Test void tamedWolfCannotBypassDisabledPvp() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        Wolf wolf=mock(Wolf.class);when(wolf.getOwner()).thenReturn(visitor);
        EntityDamageByEntityEvent event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(owner);when(event.getDamager()).thenReturn(wolf);
        listener().onEntityDamageByEntity(event);verify(event).setCancelled(true);
    }
    @Test void harmfulSplashPotionDoesNotPoisonProtectedPlayerButStillAffectsUnclaimedPlayer() {
        Chunk inside=chunk(0,0),outside=chunk(1,0);Player owner=player("Owner",inside),attacker=player("Attacker",outside),other=player("Other",outside);claims.claimChunk(owner,inside);
        ThrownPotion potion=mock(ThrownPotion.class);when(potion.getShooter()).thenReturn(attacker);when(potion.getEffects()).thenAnswer(i->List.of(new PotionEffect(PotionEffectType.POISON,200,0)));
        PotionSplashEvent event=mock(PotionSplashEvent.class);when(event.getPotion()).thenReturn(potion);when(event.getAffectedEntities()).thenReturn(List.of(owner,other));
        listener().potionSplash(event);verify(event).setIntensity(owner,0);verify(event,never()).setIntensity(other,0);
    }
    @Test void beneficialSplashAndEnabledPvpRemainUsable() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),attacker=player("Attacker",inside);claims.claimChunk(owner,inside);
        ThrownPotion potion=mock(ThrownPotion.class);when(potion.getShooter()).thenReturn(attacker);when(potion.getEffects()).thenAnswer(i->List.of(new PotionEffect(PotionEffectType.HEAL,1,0)));
        PotionSplashEvent event=mock(PotionSplashEvent.class);when(event.getPotion()).thenReturn(potion);when(event.getAffectedEntities()).thenReturn(List.of(owner));
        listener().potionSplash(event);verify(event,never()).setIntensity(owner,0);
        when(potion.getEffects()).thenAnswer(i->List.of(new PotionEffect(PotionEffectType.POISON,200,0)));claims.getClaimAt(inside).setChoice("pvp",true);
        listener().potionSplash(event);verify(event,never()).setIntensity(owner,0);
    }
    @Test void lingeringPoisonIsFilteredBeforeItAppliesToProtectedPlayers() {
        Chunk inside=chunk(0,0),outside=chunk(1,0);Player owner=player("Owner",inside),attacker=player("Attacker",outside);claims.claimChunk(owner,inside);
        AreaEffectCloud cloud=mock(AreaEffectCloud.class);when(cloud.getSource()).thenReturn(attacker);when(cloud.getCustomEffects()).thenAnswer(i->List.of(new PotionEffect(PotionEffectType.POISON,200,0)));
        AreaEffectCloudApplyEvent event=mock(AreaEffectCloudApplyEvent.class);when(event.getEntity()).thenReturn(cloud);
        List<LivingEntity> affected=new ArrayList<>(List.of(owner));when(event.getAffectedEntities()).thenReturn(affected);
        listener().lingeringPotion(event);assertTrue(affected.isEmpty());
    }
    @Test void visitorsCannotDamageFarmAnimalsOrLeashThemOrEnterVehicles() {
        Chunk inside=chunk(0,0);Player owner=player("Owner",inside),visitor=player("Visitor",inside);claims.claimChunk(owner,inside);
        Cow cow=mock(Cow.class);when(cow.getLocation()).thenAnswer(i->owner.getLocation());
        EntityDamageByEntityEvent damage=mock(EntityDamageByEntityEvent.class);when(damage.getEntity()).thenReturn(cow);when(damage.getDamager()).thenReturn(visitor);
        listener().onEntityDamage(damage);verify(damage).setCancelled(true);
        PlayerLeashEntityEvent leash=mock(PlayerLeashEntityEvent.class);when(leash.getEntity()).thenReturn(cow);when(leash.getPlayer()).thenReturn(visitor);
        listener().leash(leash);verify(leash).setCancelled(true);
        Vehicle boat=mock(Vehicle.class);when(boat.getLocation()).thenAnswer(i->owner.getLocation());
        VehicleEnterEvent enter=mock(VehicleEnterEvent.class);when(enter.getVehicle()).thenReturn(boat);when(enter.getEntered()).thenReturn(visitor);
        listener().vehicleEnter(enter);verify(enter).setCancelled(true);
        claims.getClaimAt(inside).grant(visitor.getUniqueId(),0);clearInvocations(damage,leash,enter);
        listener().onEntityDamage(damage);listener().leash(leash);listener().vehicleEnter(enter);
        verify(damage,never()).setCancelled(true);verify(leash,never()).setCancelled(true);verify(enter,never()).setCancelled(true);
    }
}
