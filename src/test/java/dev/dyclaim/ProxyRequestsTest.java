package dev.dyclaim;

import dev.dyclaim.platform.proxy.StatusRequests;
import dev.dyclaim.platform.proxy.StatusProtocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ProxyRequestsTest {
    @Test void discardOnlyConsumesItsOwnRequestGeneration() {
        AtomicLong now=new AtomicLong();StatusRequests requests=new StatusRequests(now::get);UUID actor=UUID.randomUUID();
        var old=requests.begin(actor,"survival").orElseThrow();now.set(StatusRequests.TIMEOUT_NANOS);
        var next=requests.begin(actor,"survival").orElseThrow();
        assertFalse(requests.discard(actor,old.id()));
        assertTrue(requests.complete(actor,"survival","survival",new StatusProtocol.Status(next.id(),"1.0.2",Set.of("claim"))));
        assertFalse(requests.discard(actor,next.id()));
    }
    @Test void onlyTheExpectedActorAndBackendCanCompleteOnce() {
        AtomicLong now=new AtomicLong();StatusRequests requests=new StatusRequests(now::get);UUID actor=UUID.randomUUID();
        var ticket=requests.begin(actor,"survival").orElseThrow();var status=new StatusProtocol.Status(ticket.id(),"1.0.2",Set.of("claim"));
        assertFalse(requests.complete(UUID.randomUUID(),"survival","survival",status));
        assertFalse(requests.complete(actor,"untrusted","survival",status));
        assertTrue(requests.complete(actor,"survival","survival",status));
        assertFalse(requests.complete(actor,"survival","survival",status));
    }
    @Test void serverSwitchOrTimeoutInvalidatesAReplyEvenIfPlayerSwitchesBack() {
        AtomicLong now=new AtomicLong();StatusRequests requests=new StatusRequests(now::get);UUID actor=UUID.randomUUID();
        var ticket=requests.begin(actor,"survival").orElseThrow();var status=new StatusProtocol.Status(ticket.id(),"1.0.2",Set.of("claim"));
        assertFalse(requests.complete(actor,"survival","lobby",status));
        assertFalse(requests.complete(actor,"survival","survival",status));
        var next=requests.begin(actor,"survival").orElseThrow();now.set(StatusRequests.TIMEOUT_NANOS);
        assertFalse(requests.complete(actor,"survival","survival",new StatusProtocol.Status(next.id(),"1.0.2",Set.of("claim"))));
    }
    @Test void admissionIsBoundedAndDisconnectRemovesThePendingRequest() {
        StatusRequests requests=new StatusRequests(()->0L);UUID first=UUID.randomUUID();assertTrue(requests.begin(first,"survival").isPresent());
        assertTrue(requests.begin(first,"survival").isEmpty());
        for(int i=1;i<StatusRequests.MAX_PENDING;i++)assertTrue(requests.begin(UUID.randomUUID(),"survival").isPresent());
        assertTrue(requests.begin(UUID.randomUUID(),"survival").isEmpty());requests.remove(first);
        assertTrue(requests.begin(UUID.randomUUID(),"survival").isPresent());
    }
}
