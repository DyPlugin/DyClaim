package dev.dyclaim;
import dev.dyclaim.util.AtomicJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentAtomicJsonTest {
    @TempDir Path dir;
    @Test void concurrentWritesToTheSameFileCannotShareATemporaryFile() throws Exception {
        Path file=dir.resolve("state.json");String content="x".repeat(256_000);
        int count=8;CyclicBarrier start=new CyclicBarrier(count);ExecutorService workers=Executors.newFixedThreadPool(count);
        try {
            var tasks=new ArrayList<Future<?>>();
            for(int n=0;n<count;n++){int id=n;tasks.add(workers.submit(()->{start.await(3,TimeUnit.SECONDS);for(int i=0;i<4;i++)AtomicJson.write(file,Map.of("writer",id,"content",content));return null;}));}
            for(Future<?> task:tasks)assertDoesNotThrow(()->task.get(15,TimeUnit.SECONDS));
            com.google.gson.JsonObject saved=AtomicJson.read(file,com.google.gson.JsonObject.class);
            assertEquals(content,saved.get("content").getAsString());
            assertTrue(saved.get("writer").getAsInt()>=0&&saved.get("writer").getAsInt()<count);
            com.google.gson.JsonObject backup=AtomicJson.read(file.resolveSibling("state.json.bak"),com.google.gson.JsonObject.class);
            assertEquals(content,backup.get("content").getAsString());
        }finally{workers.shutdownNow();}
    }
}
