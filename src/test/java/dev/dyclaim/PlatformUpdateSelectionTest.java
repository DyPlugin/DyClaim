package dev.dyclaim;
import dev.dyclaim.util.UpdateFeed;
import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PlatformUpdateSelectionTest {
    @Test void mixedLoaderMetadataCannotOfferANativeOnlyJarToBukkit(){
        String candidate=release("Mixed","1.0.3","paper","DyClaim-v1.0.3-Folia.jar").replace("[\"paper\"]","[\"paper\",\"folia\"]");
        assertNull(UpdateFeed.select("["+candidate+"]","26.2","1.0.2"));
    }
    private String release(String id,String version,String loader,String filename){
        return "{\"id\":\""+id+"\",\"version_number\":\""+version+"\",\"status\":\"listed\",\"version_type\":\"release\",\"game_versions\":[\"26.2\"],\"loaders\":[\""+loader+"\"],\"files\":[{\"filename\":\""+filename+"\"}]}";
    }
    @Test void nativePortsNeverSelectBukkitOrWrongPlatformArtifacts() {
        for(var target:new UpdateFeed.Target[]{UpdateFeed.Target.FOLIA,UpdateFeed.Target.SPONGE,UpdateFeed.Target.VELOCITY,UpdateFeed.Target.BUNGEECORD}){
            String suffix=switch(target){case FOLIA->"Folia";case SPONGE->"Sponge";case VELOCITY->"Velocity";default->"BungeeCord";};
            String loader=target.name().toLowerCase(java.util.Locale.ROOT);
            String json="["+release("Bukkit","1.0.9","paper","DyClaim-v1.0.9.jar")+","+release("WrongFile","1.0.8",loader,"DyClaim-v1.0.8.jar")+","+release("Native","1.0.3",loader,"DyClaim-v1.0.3-"+suffix+".jar")+"]";
            var found=UpdateFeed.select(json,"26.2","1.0.2",target);
            assertNotNull(found,target.toString());assertEquals("1.0.3",found.version(),target.toString());assertTrue(found.url().endsWith("/Native"));
        }
    }
    @Test void nativeQueriesAskForTheirOwnLoaderOnly() throws Exception {
        for(var target:new UpdateFeed.Target[]{UpdateFeed.Target.FOLIA,UpdateFeed.Target.SPONGE,UpdateFeed.Target.VELOCITY,UpdateFeed.Target.BUNGEECORD}){
            String query=URLDecoder.decode(UpdateFeed.requestUrl("26.2",target).getQuery(),StandardCharsets.UTF_8);
            assertTrue(query.contains("loaders=[\""+target.name().toLowerCase(java.util.Locale.ROOT)+"\"]"));
            assertFalse(query.contains("\"paper\""));
        }
    }
    @Test void unsupportedNativeArtifactDoesNotBecomeAFallbackBukkitRecommendation(){
        assertNull(UpdateFeed.select("["+release("Wrong","1.0.3","folia","DyClaim-v1.0.3.jar")+"]","26.2","1.0.2",UpdateFeed.Target.FOLIA));
    }
}
