package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.command.CommandSender;
import java.util.*;

/** Small read-only support summary; never includes paths, credentials or raw provider errors. */
public final class Diagnostics {
    private Diagnostics(){}
    public static void send(DyClaim plugin,CommandSender sender){
        MessageManager messages=plugin.getMessageManager();
        java.util.function.Function<String,String> state=key->messages.getMessage(sender,key);
        String platform=plugin.getPlatformTasks()!=null&&plugin.getPlatformTasks().isRegionThreaded()?"Folia":"Bukkit";
        line(plugin,sender,"diagnose-header",Map.of("{version}",Objects.toString(plugin.getDescription()==null?null:plugin.getDescription().getVersion(),"unknown"),"{platform}",platform));
        line(plugin,sender,"diagnose-storage",Map.of("{status}",state.apply(plugin.getClaimManager().isWritable()?"diagnose-ready":"diagnose-unavailable"),"{count}",String.valueOf(plugin.getClaimManager().getAllClaims().size())));
        String economy=!plugin.getEconomyManager().isEnabled()?"diagnose-disabled":plugin.getEconomyManager().isAvailable()?"diagnose-ready":"diagnose-unavailable";
        line(plugin,sender,"diagnose-economy",Map.of("{status}",state.apply(economy)));
        var pm=plugin.getServer().getPluginManager();
        String installed=List.of("Vault","WorldGuard","GriefPrevention","Towny","Lands","Residence","GriefDefender","floodgate","SimpleClans","PlaceholderAPI","BlueMap","dynmap","squaremap").stream().filter(pm::isPluginEnabled).collect(java.util.stream.Collectors.joining(", "));
        line(plugin,sender,"diagnose-integrations",Map.of("{plugins}",installed.isEmpty()?state.apply("access-none"):installed));
        var hooks=plugin.getIntegrations();
        if(hooks!=null){
            List<String> active=new ArrayList<>();if(hooks.placeholdersActive())active.add("PlaceholderAPI");active.addAll(hooks.activeMaps());
            line(plugin,sender,"diagnose-hooks",Map.of("{hooks}",active.isEmpty()?state.apply("access-none"):String.join(", ",active)));
        }
    }
    private static void line(DyClaim plugin,CommandSender sender,String key,Map<String,String> values){sender.sendMessage(plugin.getMessageManager().getPrefixed(sender,key,values));}
}
