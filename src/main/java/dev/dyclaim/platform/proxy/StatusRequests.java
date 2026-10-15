package dev.dyclaim.platform.proxy;
import java.util.*;
import java.util.function.LongSupplier;

/** Correlates private diagnostics; it never stores claim or economy data. */
public final class StatusRequests {
    public static final long TIMEOUT_NANOS=5_000_000_000L;
    public static final int MAX_PENDING=256;
    public record Ticket(UUID id,String backend,long createdNanos){}
    private final LongSupplier time;
    private final Map<UUID,Ticket> pending=new HashMap<>();
    public StatusRequests(LongSupplier time){this.time=Objects.requireNonNull(time);}
    public synchronized Optional<Ticket> begin(UUID actor,String backend){
        Objects.requireNonNull(actor);if(backend==null||backend.isBlank()||backend.length()>256)throw new IllegalArgumentException("Invalid backend identity");
        long now=time.getAsLong();pending.values().removeIf(ticket->expired(ticket,now));
        if(pending.containsKey(actor)||pending.size()>=MAX_PENDING)return Optional.empty();
        Ticket ticket=new Ticket(UUID.randomUUID(),backend,now);pending.put(actor,ticket);return Optional.of(ticket);
    }
    public synchronized boolean complete(UUID actor,String sourceBackend,String currentBackend,StatusProtocol.Status status){
        Ticket ticket=pending.get(actor);if(ticket==null)return false;
        if(expired(ticket,time.getAsLong())||!ticket.backend().equals(currentBackend)){pending.remove(actor);return false;}
        if(!ticket.backend().equals(sourceBackend)||!ticket.id().equals(status.requestId()))return false;
        pending.remove(actor);return true;
    }
    public synchronized boolean expire(UUID actor,UUID id){Ticket ticket=pending.get(actor);if(ticket==null||!ticket.id().equals(id)||!expired(ticket,time.getAsLong()))return false;pending.remove(actor);return true;}
    public synchronized void remove(UUID actor){pending.remove(actor);}
    public synchronized boolean discard(UUID actor,UUID id){Ticket ticket=pending.get(actor);if(ticket==null||!ticket.id().equals(id))return false;pending.remove(actor);return true;}
    public synchronized void clear(){pending.clear();}
    private boolean expired(Ticket ticket,long now){return now-ticket.createdNanos()>=TIMEOUT_NANOS;}
}
