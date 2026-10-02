package dev.dyclaim;

import dev.dyclaim.util.Rules;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void durationsUseWallClockAndExactArithmetic(){assertEquals(1_801_000,Rules.expiry("30m",1000,Long.MAX_VALUE,true));assertEquals(7_201_000,Rules.expiry("2h",1000,Long.MAX_VALUE,true));assertEquals(604_801_000,Rules.expiry("7d",1000,Long.MAX_VALUE,true));assertEquals(0,Rules.expiry("permanent",1000,1,true));}
    @ParameterizedTest @ValueSource(strings={"0m","-1h","1","1s","01d","1.5h","9223372036854775807d","999999999999999999999h","permanent",""}) void rejectsMalformedAndForbiddenPermanent(String text){assertThrows(IllegalArgumentException.class,()->Rules.expiry(text,100,Long.MAX_VALUE,false));}
    @Test void durationLimitsAndClockOverflow(){assertThrows(IllegalArgumentException.class,()->Rules.expiry("2d",0,86_400_000,false));assertThrows(IllegalArgumentException.class,()->Rules.expiry("1m",Long.MAX_VALUE,Long.MAX_VALUE,false));}
    @Test void spacingUsesEmptyChunksIncludingDiagonalsAndNegativeCoordinates(){assertTrue(Rules.spacing(-1,-1,0,0,0));assertFalse(Rules.spacing(-1,-1,0,0,1));assertTrue(Rules.spacing(-1,-1,1,1,1));assertTrue(Rules.spacing(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0,1));assertFalse(Rules.spacing(1,1,1,1,0));}
    @ParameterizedTest @ValueSource(strings={"0","-1","NaN","Infinity","1.001","1000001"}) void rejectsUnsafeMarketPrices(String value){assertThrows(IllegalArgumentException.class,()->Rules.marketPrice(value,1,1_000_000));}
    @Test void moneyAndTaxRoundDeterministically(){assertEquals(1000,Rules.marketPrice("1000.00",1,1_000_000));assertEquals(1.01,Rules.money(1.005));assertThrows(IllegalArgumentException.class,()->Rules.money(Double.NaN));assertEquals(950,Rules.money(1000-Rules.money(1000*5/100)));}
}
