package dev.dyclaim;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentFeatureStateTest extends ManagerFixture {
    @Test void simultaneousLanguageChangesSurviveSavingAndReload() throws Exception {
        List<Player> players=new ArrayList<>();
        for(int i=0;i<256;i++)players.add(player("Language"+i,chunk(0,0)));
        ExecutorService workers=Executors.newFixedThreadPool(8);CyclicBarrier start=new CyclicBarrier(8);
        List<Future<?>> results=new ArrayList<>();
        try{
            for(int worker=0;worker<8;worker++){
                int lane=worker;results.add(workers.submit(()->{
                    start.await(5,TimeUnit.SECONDS);
                    for(int i=lane;i<players.size();i+=8)features.handle(players.get(i),new String[]{"lang",i%2==0?"en":"tr"});
                    return null;
                }));
            }
            for(Future<?> result:results)result.get(30,TimeUnit.SECONDS);
        }finally{workers.shutdownNow();}
        features.save();var reloaded=new dev.dyclaim.manager.FeatureManager(plugin);
        for(int i=0;i<players.size();i++)assertEquals(i%2==0?"en":"tr",reloaded.language(players.get(i).getUniqueId()));
    }
}
