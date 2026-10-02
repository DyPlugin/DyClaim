package dev.dyclaim;
import dev.dyclaim.util.ReleaseVersion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseVersionTest {
    @Test void stableDoesNotAdvertiseBetaOrRcAsUpdates(){assertFalse(ReleaseVersion.newer("v1.0.0-Beta","1.0.0"));assertFalse(ReleaseVersion.newer("1.0.0-RC.1","1.0.0"));assertFalse(ReleaseVersion.newer("v1.0.0","1.0.0"));}
    @Test void stableUpgradesBetaAndRc(){assertTrue(ReleaseVersion.newer("1.0.0","v1.0.0-Beta"));assertTrue(ReleaseVersion.newer("1.0.0","1.0.0-RC.1"));assertTrue(ReleaseVersion.newer("1.0.1","1.0.0"));assertTrue(ReleaseVersion.newer("1.0.0-RC.2","1.0.0-RC.1"));}
    @Test void unknownAndOverflowTagsAreIgnored(){assertFalse(ReleaseVersion.newer("latest","1.0.0"));assertFalse(ReleaseVersion.newer("999999999999.0.0","1.0.0"));}
}
