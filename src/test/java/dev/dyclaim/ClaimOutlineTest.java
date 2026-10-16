package dev.dyclaim;

import dev.dyclaim.visualizer.ClaimOutline;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClaimOutlineTest {
    @Test void adjacentChunksHaveNoSharedInternalEdges(){
        var start=new ClaimOutline.Cell(0,0);var east=new ClaimOutline.Cell(1,0);
        var edges=ClaimOutline.connected(start,4,Set.of(start,east)::contains);
        assertEquals(6,edges.size());
        assertFalse(edges.contains(new ClaimOutline.Edge(start,ClaimOutline.Side.EAST)));
        assertFalse(edges.contains(new ClaimOutline.Edge(east,ClaimOutline.Side.WEST)));
    }
    @Test void diagonalOrDisconnectedClaimsAreNotMerged(){
        var start=new ClaimOutline.Cell(-1,-1);var other=new ClaimOutline.Cell(0,0);
        assertEquals(4,ClaimOutline.connected(start,4,Set.of(start,other)::contains).size());
    }
    @Test void largeLandIsBoundedWithoutArtificialEdgesAtTheCutoff(){
        var calls=new AtomicInteger();
        var edges=ClaimOutline.connected(new ClaimOutline.Cell(0,0),8,c->{calls.incrementAndGet();return true;});
        assertTrue(edges.isEmpty());assertTrue(calls.get()<=2000,"Traversal must remain local and bounded");
    }
    @Test void unclaimedChunkStillShowsFourEdges(){
        assertEquals(4,ClaimOutline.single(new ClaimOutline.Cell(0,0)).size());
    }
}
