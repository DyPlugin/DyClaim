package dev.dyclaim.api;

import dev.dyclaim.model.ClaimData;
import java.util.*;

/** Detached snapshot backing the public {@link Claim} interface; callers never touch live claim state. */
final class ClaimView implements Claim {
    private final ClaimData data;
    private ClaimView(ClaimData data){this.data=data;}
    static Claim of(ClaimData live){return live==null?null:new ClaimView(live.snapshot());}
    public UUID getId(){return data.getId();}
    public String getWorld(){return data.getWorld();}
    public int getChunkX(){return data.getChunkX();}
    public int getChunkZ(){return data.getChunkZ();}
    public UUID getOwner(){return data.getOwnerUUID();}
    public String getOwnerName(){return data.getOwnerName();}
    public Optional<UUID> getCoowner(){return Optional.ofNullable(data.getCoowner());}
    public Optional<String> getName(){return Optional.ofNullable(data.getName());}
    public long getClaimedAt(){return data.getClaimedAt();}
    public Set<UUID> getTrustedPlayers(){
        long now=System.currentTimeMillis();Set<UUID> out=new HashSet<>();
        data.getTrustGrants().forEach((uuid,grant)->{if(grant.allowsExpiry(now))out.add(uuid);});
        return Collections.unmodifiableSet(out);
    }
    public boolean allows(UUID player,String right){return player!=null&&data.allows(player,right);}
    @Override public String toString(){return "Claim["+data.getChunkKey()+" owner="+data.getOwnerName()+"]";}
}
