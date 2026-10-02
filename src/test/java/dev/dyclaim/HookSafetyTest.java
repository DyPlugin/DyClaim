package dev.dyclaim;
import dev.dyclaim.hook.ClaimPluginHooks;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HookSafetyTest extends ManagerFixture {
    public static class BrokenTowny {public Object getTownBlock(Location ignored){throw new IllegalStateException("provider unavailable");}}
    public static class Manager {private final Object residence;public Manager(Object value){residence=value;}public Map<String,Object> getResidences(){return Map.of("small",residence);}}
    public static class Residence {private final Area area;public Residence(Area value){area=value;}public Area[] getAreaArray(){return new Area[]{area};}}
    public static class Area {private final Location low,high;public Area(Location a,Location b){low=a;high=b;}public Location getLowLocation(){return low;}public Location getHighLocation(){return high;}}
    private void field(String name,Object value)throws Exception{var field=ClaimPluginHooks.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
    @Test void queryExceptionBlocksAcquisition()throws Exception{
        field("townyAvailable",true);field("townyApiInstance",new BrokenTowny());field("townyGetTownBlock",BrokenTowny.class.getMethod("getTownBlock",Location.class));
        try{assertTrue(ClaimPluginHooks.isRegionProtected(chunk(0,0)));}finally{field("townyAvailable",false);field("townyApiInstance",null);field("townyGetTownBlock",null);}
    }
    @Test void residenceAtChunkCornerIsDetectedAtAnyHeight()throws Exception{
        when(world.getUID()).thenReturn(UUID.randomUUID());Location low=new Location(world,1,150,1),high=new Location(world,2,160,2);
        field("residenceAvailable",true);field("residenceManager",new Manager(new Residence(new Area(low,high))));
        try{assertTrue(ClaimPluginHooks.isRegionProtected(chunk(0,0)));assertFalse(ClaimPluginHooks.isRegionProtected(chunk(1,0)));}finally{field("residenceAvailable",false);field("residenceManager",null);}
    }
}
