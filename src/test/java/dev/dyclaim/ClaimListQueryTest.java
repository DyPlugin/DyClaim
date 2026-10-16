package dev.dyclaim;

import dev.dyclaim.model.ClaimData;
import dev.dyclaim.util.ClaimListQuery;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimListQueryTest {
    ClaimData claim(String world,int x,String name,Double price){
        var c=new ClaimData(UUID.randomUUID(),"Owner",world,x,0);c.setName(name);c.setMarketPrice(price);return c;
    }
    @Test void filteringKeepsOriginalTeleportNumbersAndDoesNotMutateClaims(){
        var all=List.of(claim("world",0,null,null),claim("world",1,"Home",100.0),claim("nether",0,"Home",null));
        var q=ClaimListQuery.parse(new String[]{"list","world","world","sale","listed","name","ho"});
        var rows=q.select(all);assertEquals(1,rows.size());assertEquals(2,rows.get(0).number());assertSame(all.get(1),rows.get(0).claim());assertNull(all.get(0).getName());
    }
    @Test void turkishAndAsciiFilterWordsWorkWithoutTranslatingValues(){
        var c=claim("Dünya",0,"İnşa Alanı",null);
        var q=ClaimListQuery.parse(new String[]{"liste","1","dunya","Dünya","isim","insa","satis","satista-degil"});
        assertEquals(1,q.select(List.of(c)).size());
    }
    @Test void legacyPagesAndEmptyFilteredResultsRemainWellDefined(){
        assertEquals(2,ClaimListQuery.parse(new String[]{"list","2"}).page());
        assertEquals(1,ClaimListQuery.parse(new String[]{"list"}).page());
        assertTrue(ClaimListQuery.parse(new String[]{"list","name","missing"}).select(List.of(claim("world",0,null,null))).isEmpty());
    }
    @Test void malformedRepeatedAndOverflowArgumentsAreRejected(){
        for(String[] args:List.of(new String[]{"list","0"},new String[]{"list","2147483648"},new String[]{"list","world"},new String[]{"list","world","a","world","b"},new String[]{"list","sale","maybe"},new String[]{"list","oops"}))
            assertThrows(IllegalArgumentException.class,()->ClaimListQuery.parse(args),Arrays.toString(args));
    }
}
