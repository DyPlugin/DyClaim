package dev.dyclaim.util;
import java.util.List;
public final class WarningRules {
    private WarningRules(){}
    /** Returns zero if the shared cooldown denies this warning. Window starts exclusively. */
    public static int record(List<Long> times,long now,long cooldown,long window){
        times.removeIf(time->time<=now-window);
        if(!times.isEmpty()&&now-times.get(times.size()-1)<cooldown)return 0;
        times.add(now);return times.size();
    }
}
