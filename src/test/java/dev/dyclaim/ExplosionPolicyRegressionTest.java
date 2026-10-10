package dev.dyclaim;

import dev.dyclaim.listener.ClaimProtectionListener;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExplosionPolicyRegressionTest extends ManagerFixture {
    private LargeFireball ghastFireball() {
        LargeFireball fireball=mock(LargeFireball.class);
        Ghast ghast=mock(Ghast.class);when(fireball.getShooter()).thenReturn(ghast);
        return fireball;
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ghastFireballBlockDamageUsesMobExplosionChoice(boolean mobExplosions) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var claim=claims.getClaimAt(inside);claim.setChoice("explosions",!mobExplosions);claim.setChoice("mob-explosions",mobExplosions);
        Block block=mock(Block.class);when(block.getChunk()).thenReturn(inside);
        List<Block> affected=new ArrayList<>(List.of(block));
        var fireball=ghastFireball();EntityExplodeEvent event=mock(EntityExplodeEvent.class);when(event.getEntity()).thenReturn(fireball);when(event.blockList()).thenReturn(affected);
        new ClaimProtectionListener(plugin).onEntityExplode(event);
        assertEquals(mobExplosions?List.of(block):List.of(),affected);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ghastFireballEntityDamageUsesMobExplosionChoice(boolean mobExplosions) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var claim=claims.getClaimAt(inside);claim.setChoice("explosions",!mobExplosions);claim.setChoice("mob-explosions",mobExplosions);
        Cow cow=mock(Cow.class);var location=owner.getLocation();when(cow.getLocation()).thenReturn(location);
        EntityDamageByEntityEvent event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(cow);
        var fireball=ghastFireball();when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);when(event.getDamager()).thenReturn(fireball);
        new ClaimProtectionListener(plugin).explosionEntityDamage(event);
        verify(event,mobExplosions?never():times(1)).setCancelled(true);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ghastFireballHangingDamageUsesMobExplosionChoice(boolean mobExplosions) {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var claim=claims.getClaimAt(inside);claim.setChoice("explosions",!mobExplosions);claim.setChoice("mob-explosions",mobExplosions);
        Painting painting=mock(Painting.class);var location=owner.getLocation();when(painting.getLocation()).thenReturn(location);
        HangingBreakByEntityEvent event=mock(HangingBreakByEntityEvent.class);when(event.getEntity()).thenReturn(painting);
        var fireball=ghastFireball();when(event.getCause()).thenReturn(HangingBreakEvent.RemoveCause.EXPLOSION);when(event.getRemover()).thenReturn(fireball);
        new ClaimProtectionListener(plugin).hangingExplosion(event);
        verify(event,mobExplosions?never():times(1)).setCancelled(true);
    }

    @Test void playerFireballKeepsOrdinaryExplosionChoice() {
        var inside=chunk(0,0);var owner=player("Owner",inside);claims.claimChunk(owner,inside);
        var claim=claims.getClaimAt(inside);claim.setChoice("explosions",true);claim.setChoice("mob-explosions",false);
        Block block=mock(Block.class);when(block.getChunk()).thenReturn(inside);
        List<Block> affected=new ArrayList<>(List.of(block));
        LargeFireball fireball=mock(LargeFireball.class);when(fireball.getShooter()).thenReturn(owner);
        EntityExplodeEvent event=mock(EntityExplodeEvent.class);when(event.getEntity()).thenReturn(fireball);when(event.blockList()).thenReturn(affected);
        new ClaimProtectionListener(plugin).onEntityExplode(event);
        assertEquals(List.of(block),affected);
    }
}
