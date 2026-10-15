package dev.dyclaim;

import dev.dyclaim.platform.proxy.StatusProtocol;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProxyProtocolTest {
    @Test void requestAndStatusRoundTripRetainTheCorrelationAndCapabilities() throws Exception {
        UUID id=UUID.randomUUID();
        assertEquals(id,StatusProtocol.decodeRequest(StatusProtocol.request(id)));
        StatusProtocol.Status status=new StatusProtocol.Status(id,"1.0.2",Set.of("claim","trust","protection"));
        assertEquals(status,StatusProtocol.decodeStatus(StatusProtocol.response(status)));
    }
    @Test void malformedVersionOversizedAndTrailingPayloadsAreRejected() throws Exception {
        byte[] good=StatusProtocol.request(UUID.randomUUID());
        byte[] unknown=good.clone();unknown[4]=99;
        assertThrows(IOException.class,()->StatusProtocol.decodeRequest(unknown));
        assertThrows(IOException.class,()->StatusProtocol.decodeRequest(Arrays.copyOf(good,good.length-1)));
        assertThrows(IOException.class,()->StatusProtocol.decodeRequest(Arrays.copyOf(good,good.length+1)));
        assertThrows(IOException.class,()->StatusProtocol.decodeRequest(new byte[StatusProtocol.MAX_BYTES+1]));
    }
    @Test void aStatusResponseCannotBeUsedAsARequest() throws Exception {
        byte[] response=StatusProtocol.response(new StatusProtocol.Status(UUID.randomUUID(),"1.0.2",Set.of("claim")));
        assertThrows(IOException.class,()->StatusProtocol.decodeRequest(response));
    }
    @Test void arbitraryTextOrAnUnboundedCapabilityListIsNotAccepted() {
        assertThrows(IllegalArgumentException.class,()->StatusProtocol.response(new StatusProtocol.Status(UUID.randomUUID(),"1.0.2\nrun console",Set.of("claim"))));
        Set<String> capabilities=new HashSet<>();for(int i=0;i<40;i++)capabilities.add("feature-"+i);
        assertThrows(IllegalArgumentException.class,()->StatusProtocol.response(new StatusProtocol.Status(UUID.randomUUID(),"1.0.2",capabilities)));
    }
}
