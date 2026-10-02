package dev.dyclaim;
import dev.dyclaim.util.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WarningRulesTest {
    @Test void sharedCooldownAllowsThirdWarningAtTwoMinutesWithinThreeMinuteWindow(){List<Long> times=new ArrayList<>();assertEquals(1,WarningRules.record(times,0,60000,180000));assertEquals(0,WarningRules.record(times,59999,60000,180000));assertEquals(2,WarningRules.record(times,60000,60000,180000));assertEquals(0,WarningRules.record(times,60001,60000,180000));assertEquals(3,WarningRules.record(times,120000,60000,180000));}
    @Test void oldWarningsExpireOnBoundary(){List<Long> times=new ArrayList<>(List.of(0L,60000L));assertEquals(1,WarningRules.record(times,240000,60000,180000));assertEquals(List.of(240000L),times);}
}
