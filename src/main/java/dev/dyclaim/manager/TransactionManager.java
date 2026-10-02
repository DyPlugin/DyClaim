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
        public String id, kind, chunk, stage;
        public UUID payer, receiver;
        public double debit, credit;
        public long at;
    }
    private final DyClaim plugin;
    private final Path file;
    private final Map<String,Entry> entries;
    private final Set<String> locked=new HashSet<>();
    public TransactionManager(DyClaim plugin) {
        this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("transactions.json");
        try{entries=Files.exists(file)?AtomicJson.read(file,new TypeToken<Map<String,Entry>>(){}.getType()):new LinkedHashMap<>();}
        catch(IOException ex){throw new IllegalStateException("Transaction journal invalid; restore a verified backup",ex);}
        for(Entry entry:entries.values()) {
            if(entry==null||entry.chunk==null||entry.stage==null||entry.id==null||!Double.isFinite(entry.debit)||!Double.isFinite(entry.credit)||entry.debit<0||entry.credit<0)
                throw new IllegalStateException("Invalid transaction journal entry");
            if(!terminal(entry.stage)){locked.add(entry.chunk);plugin.getLogger().severe("Unresolved transaction "+entry.id+" ("+entry.stage+") locks "+entry.chunk+". Reconcile provider records; do not retry blindly.");}
        }
    }
    private boolean terminal(String stage){return Set.of("complete","rejected","compensated").contains(stage);}
    public boolean isLocked(String key){return locked.contains(key);}
    public List<Entry> unresolved(){return entries.values().stream().filter(e->!terminal(e.stage)).toList();}
    private void save(){try{AtomicJson.write(file,entries);}catch(IOException ex){throw new IllegalStateException("Cannot persist transaction journal",ex);}}
    private void stage(Entry entry,String stage){entry.stage=stage;save();if(terminal(stage))locked.remove(entry.chunk);else locked.add(entry.chunk);}
    public boolean execute(String kind,String key,UUID payer,UUID receiver,double debit,double credit,Runnable mutation) {
        if(isLocked(key)||!Double.isFinite(debit)||!Double.isFinite(credit)||debit<0||credit<0)return false;
        archiveCompleted();
        Entry entry=new Entry();entry.id=UUID.randomUUID().toString();entry.kind=kind;entry.chunk=key;entry.payer=payer;entry.receiver=receiver;
        entry.debit=debit;entry.credit=credit;entry.at=System.currentTimeMillis();entry.stage="prepared";
        entries.put(entry.id,entry);locked.add(key);save();
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
            stage(entry,"mutation-in-flight");mutation.run();plugin.getClaimManager().saveAllSync();stage(entry,"complete");return true;
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
