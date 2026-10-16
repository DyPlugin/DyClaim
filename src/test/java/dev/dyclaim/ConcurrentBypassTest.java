package dev.dyclaim;
import dev.dyclaim.manager.AccessManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentBypassTest extends ManagerFixture {
    @Test void simultaneousExplicitBypassDisablesCannotFallBackToEnabledDefault() throws Exception {
        List<Player> actors=new ArrayList<>();
        for(int i=0;i<128;i++){var actor=player("Bypass"+i,chunk(0,0));when(actor.hasPermission("dyclaim.admin.bypass")).thenReturn(true);actors.add(actor);}
        values.set("admin.bypass.enabled-by-default",true);var workers=Executors.newFixedThreadPool(8);
        try{for(int round=0;round<24;round++){
            var manager=new AccessManager(plugin);var barrier=new CyclicBarrier(8);List<Future<?>> futures=new ArrayList<>();
            for(int lane=0;lane<8;lane++){int offset=lane;futures.add(workers.submit(()->{barrier.await(5,TimeUnit.SECONDS);for(int i=offset;i<actors.size();i+=8)manager.setBypass(actors.get(i),false);return null;}));}
            for(var future:futures)future.get(8,TimeUnit.SECONDS);
            for(var actor:actors)assertFalse(manager.bypass(actor),"Explicit bypass disable was lost for "+actor.getName());
        }}finally{workers.shutdownNow();}
    }
}
