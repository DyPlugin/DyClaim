package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.util.AtomicJson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Persist intent before external payments. Never replay ambiguous payments after restart. */
public class TransactionManager {
    public static class Entry {
        public String id, kind, chunk;
        public volatile String stage;
        public UUID payer, receiver;
        public double debit, credit;
        public long at;
    }
    private final DyClaim plugin;
    private final Path file;
    private final Map<String,Entry> entries;
    private final java.util.concurrent.ConcurrentMap<String,Object> locked=new java.util.concurrent.ConcurrentHashMap<>();
    private final Object journalLock=new Object();
    private final ThreadLocal<Entry> mutationOwner=new ThreadLocal<>();
    public final class Reservation implements AutoCloseable {
        private final String key;
        private final java.util.concurrent.atomic.AtomicBoolean consumed=new java.util.concurrent.atomic.AtomicBoolean();
        private Reservation(String key){this.key=key;}
        @Override public void close(){locked.remove(key,this);}
    }
    public Reservation reserve(String key){synchronized(plugin.getClaimManager()){Reservation reservation=new Reservation(key);return locked.putIfAbsent(key,reservation)==null?reservation:null;}}
    public boolean ownsMutation(String key){Entry entry=mutationOwner.get();return entry!=null&&entry.chunk.equals(key)&&locked.get(key)==entry;}
    @FunctionalInterface public interface JournalWriter {void write(Path file,Map<String,Entry> entries)throws IOException;}
    private final JournalWriter writer;
    public TransactionManager(DyClaim plugin) {
        this(plugin,(file,entries)->AtomicJson.write(file,entries));
    }
    public TransactionManager(DyClaim plugin,JournalWriter writer) {
        this.plugin=plugin;this.writer=java.util.Objects.requireNonNull(writer);file=plugin.getDataFolder().toPath().resolve("transactions.json");
        try{entries=Files.exists(file)?AtomicJson.read(file,new TypeToken<Map<String,Entry>>(){}.getType()):new LinkedHashMap<>();}
        catch(IOException ex){throw new IllegalStateException("Transaction journal invalid; restore a verified backup",ex);}
        for(Entry entry:entries.values()) {
            if(entry==null||entry.chunk==null||entry.stage==null||entry.id==null||!Double.isFinite(entry.debit)||!Double.isFinite(entry.credit)||entry.debit<0||entry.credit<0)
                throw new IllegalStateException("Invalid transaction journal entry");
            if(!terminal(entry.stage)){locked.put(entry.chunk,entry);plugin.getLogger().severe("Unresolved transaction "+entry.id+" ("+entry.stage+") locks "+entry.chunk+". Reconcile provider records; do not retry blindly.");}
        }
    }
    private boolean terminal(String stage){return Set.of("complete","rejected","compensated").contains(stage);}
    public boolean isLocked(String key){return locked.containsKey(key);}
    public List<Entry> unresolved(){synchronized(journalLock){return entries.values().stream().filter(e->!terminal(e.stage)).toList();}}
    private void save(){try{writer.write(file,entries);}catch(IOException ex){throw new IllegalStateException("Cannot persist transaction journal",ex);}}
    private void stage(Entry entry,String stage){
        synchronized(journalLock){
        String previous=entry.stage;entry.stage=stage;
        try{save();}catch(RuntimeException ex){entry.stage=previous;throw ex;}
        if(terminal(stage))locked.remove(entry.chunk,entry);else locked.putIfAbsent(entry.chunk,entry);
        }
    }
    public boolean execute(String kind,String key,UUID payer,UUID receiver,double debit,double credit,Runnable mutation) {
        try(Reservation reservation=reserve(key)){
            return reservation!=null&&execute(reservation,kind,key,payer,receiver,debit,credit,mutation);
        }
    }
    /** Admission can reserve a chunk before payment; durable intent takes over that exact lease. */
    public boolean execute(Reservation reservation,String kind,String key,UUID payer,UUID receiver,double debit,double credit,Runnable mutation) {
        // A previous item in a batch may have disabled persistence synchronously.
        if(!plugin.getClaimManager().isWritable()||!Double.isFinite(debit)||!Double.isFinite(credit)||debit<0||credit<0)return false;
        if(reservation==null||!reservation.key.equals(key)||!reservation.consumed.compareAndSet(false,true))return false;
        Entry entry=new Entry();entry.id=UUID.randomUUID().toString();entry.kind=kind;entry.chunk=key;entry.payer=payer;entry.receiver=receiver;
        entry.debit=debit;entry.credit=credit;entry.at=System.currentTimeMillis();entry.stage="prepared";
        if(!locked.replace(key,reservation,entry))return false;
        synchronized(journalLock){
            try{archiveCompleted();}catch(RuntimeException ex){locked.remove(key,entry);throw ex;}
            // Keep the reservation and visible intent if its durable write fails.
            entries.put(entry.id,entry);save();
        }
        EconomyManager economy=plugin.getEconomyManager();
        if(payer!=null&&debit>0) {
            stage(entry,"withdraw-in-flight");
            if(!economy.withdraw(payer,debit)){stage(entry,economy.wasUncertain()?"withdraw-uncertain":"rejected");return false;}
            stage(entry,"withdrawn");
        }
        if(receiver!=null&&credit>0) {
            stage(entry,"deposit-in-flight");
            if(!economy.deposit(receiver,credit)) {
                if(economy.wasUncertain()){stage(entry,"deposit-uncertain");return false;}
                if(payer!=null&&debit>0) {
                    stage(entry,"compensation-in-flight");
                    if(!economy.deposit(payer,debit)){stage(entry,"compensation-unresolved");return false;}
                    stage(entry,"compensated");
                }else stage(entry,"rejected");
                return false;
            }
            stage(entry,"deposited");
        }
        try {
            stage(entry,"mutation-in-flight");Entry previous=mutationOwner.get();mutationOwner.set(entry);
            try{mutation.run();}finally{if(previous==null)mutationOwner.remove();else mutationOwner.set(previous);}
            plugin.getClaimManager().saveAllSync();stage(entry,"complete");return true;
        }catch(RuntimeException ex){plugin.getLogger().severe("Transaction "+entry.id+" requires reconciliation: "+ex.getMessage());return false;}
    }
    private void archiveCompleted(){
        if(entries.size()<1000)return;
        Map<String,Entry> completed=new LinkedHashMap<>();entries.forEach((id,e)->{if(terminal(e.stage))completed.put(id,e);});
        if(completed.isEmpty())return;
        try{AtomicJson.write(plugin.getDataFolder().toPath().resolve("transactions/archive-"+UUID.randomUUID()+".json"),completed);}
        catch(IOException ex){throw new IllegalStateException("Cannot archive transaction history",ex);}
        completed.keySet().forEach(entries::remove);save();
    }
}
