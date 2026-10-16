package dev.dyclaim;
import dev.dyclaim.manager.UpdateChecker;
import dev.dyclaim.util.UpdateFeed;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Release103Test {
    @Test void visibleVersionHasNoVPrefix(){assertEquals("DyClaim-1.0.3",UpdateChecker.displayVersion("v1.0.3"));}
    @Test void checkerAcceptsNewFilenamesAndStillAcceptsHistoricalOnes(){
        String base="{\"id\":\"New\",\"version_number\":\"1.0.3-Folia\",\"status\":\"listed\",\"version_type\":\"release\",\"game_versions\":[\"26.3\"],\"loaders\":[\"folia\"],\"files\":[{\"filename\":\"DyClaim-1.0.3-Folia.jar\"}]}";
        assertNotNull(UpdateFeed.select("["+base+"]","26.3","1.0.2",UpdateFeed.Target.FOLIA));
        assertNotNull(UpdateFeed.select("["+base.replace("DyClaim-1.0.3","DyClaim-v1.0.3")+"]","26.3","1.0.2",UpdateFeed.Target.FOLIA));
    }
}
