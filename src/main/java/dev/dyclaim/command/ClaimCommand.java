package dev.dyclaim.command;

import dev.dyclaim.DyClaim;
import dev.dyclaim.manager.MessageManager;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

/** Keeps the original roots and bilingual aliases; features share one command path. */
public class ClaimCommand implements CommandExecutor,TabCompleter {
    private final DyClaim plugin;
    public ClaimCommand(DyClaim plugin){this.plugin=plugin;}
    private void send(Player p,String key){p.sendMessage(plugin.getMessageManager().getPrefixed(p,key));}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)){sender.sendMessage(plugin.getMessageManager().getPrefixed(sender,"player-only"));return true;}
        args=dev.dyclaim.util.CommandWords.normalize(args);
        try {
            if(plugin.getFeatureManager().handle(p,args))return true;
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "sell","sat" -> SellHelper.request(plugin,p,false);
                case "confirm","onayla" -> {if(!plugin.getConfirmationManager().confirm(p.getUniqueId(),args.length<2?null:args[1]))send(p,"confirm-none");}
                case "cancel","deny","reddet" -> {if(!plugin.getConfirmationManager().deny(p.getUniqueId(),args.length<2?null:args[1]))send(p,"confirm-none");}
                case "see","gor","gör" -> {
                    if(!p.hasPermission("dyclaim.see")){send(p,"no-permission");return true;}
                    if(plugin.getChunkVisualizer().isViewing(p.getUniqueId())){send(p,"see-already");return true;}
                    plugin.getChunkVisualizer().showChunkBorders(p);
                    p.sendMessage(plugin.getMessageManager().getPrefixed(p,"see-showing",Map.of("{duration}",String.valueOf(plugin.getConfigManager().getVisualizationDuration()))));
                }
                case "admin" -> admin(p,args);
                case "help","yardim","yardım" -> help(p,false);
                default -> send(p,"unknown-command");
            }
        }catch(IllegalArgumentException ex){send(p,"invalid-input");}
        catch(RuntimeException ex){plugin.getLogger().severe("Claim action failed: "+ex.getMessage());send(p,"transaction-failed");}
        return true;
    }
    private void admin(Player p,String[] args) {
        if(!p.hasPermission("dyclaim.admin")){send(p,"no-permission");return;}
        if(args.length<2){help(p,true);return;}
        var cfg=plugin.getConfigManager();
        switch(args[1].toLowerCase(Locale.ROOT)) {
            case "enable","ac","aç" -> {cfg.setAllowClaiming(true);send(p,"admin-claiming-enabled");}
            case "disable","kapat" -> {cfg.setAllowClaiming(false);send(p,"admin-claiming-disabled");}
            case "price","fiyat" -> {
                if(args.length!=3){send(p,"admin-usage-price");return;}
                double previous=cfg.getClaimPrice(),price=dev.dyclaim.util.Rules.money(Double.parseDouble(args[2]));cfg.setClaimPrice(price);
                if(cfg.isAutoRefundPriceDifference()&&previous>price)plugin.getClaimManager().refundPriceDifference(previous-price);
                p.sendMessage(plugin.getMessageManager().getPrefixed(p,"admin-price-set",Map.of("{price}",plugin.getEconomyManager().formatMoney(price))));
            }
            case "pricediff","farkver" -> {
                double previous=args.length>=3?Double.parseDouble(args[2]):cfg.getPreviousClaimPrice();
                if(!Double.isFinite(previous)||previous<=cfg.getClaimPrice()){send(p,"invalid-input");return;}
                int owners=plugin.getClaimManager().refundPriceDifference(previous-cfg.getClaimPrice());
                p.sendMessage(plugin.getMessageManager().getPrefixed(p,"admin-price-diff-success",Map.of("{count}",String.valueOf(owners),"{refund}",plugin.getEconomyManager().formatMoney(previous-cfg.getClaimPrice()))));
            }
            case "cooldown" -> {if(args.length!=3){send(p,"admin-usage-cooldown");return;}cfg.setCooldownSeconds(Integer.parseInt(args[2]));send(p,"operation-success");}
            case "prefix" -> {cfg.setPrefix(args.length<3?"&7[&dDyClaim&7]":String.join(" ",Arrays.copyOfRange(args,2,args.length)));send(p,"admin-prefix-set");}
            case "economy","ekonomi" -> {if(args.length!=3){send(p,"invalid-input");return;}boolean value=toggle(args[2]);cfg.setEconomyEnabled(value);send(p,value?"admin-economy-enabled":"admin-economy-disabled");}
            case "lang","dil" -> {if(args.length!=3||!Set.of("auto","en","tr").contains(args[2])){send(p,"invalid-input");return;}cfg.setLang(args[2]);plugin.getMessageManager().reload();send(p,"operation-success");}
            case "blacklist","karaliste" -> {
                if(args.length!=4){send(p,"admin-usage-blacklist");return;}String world=args[3];if(Bukkit.getWorld(world)==null){p.sendMessage(plugin.getMessageManager().getPrefixed(p,"admin-world-not-found",Map.of("{world}",world)));return;}
                switch(args[2]){case "add","ekle"->cfg.addBlacklistedWorld(world);case "remove","cikar","çıkar"->cfg.removeBlacklistedWorld(world);default->throw new IllegalArgumentException("blacklist");}send(p,"operation-success");
            }
            case "pvp","explosion","patlama","mob" -> {
                if(args.length<3||args.length>4){send(p,"invalid-input");return;}String key=switch(args[1]){case "pvp"->"pvp";case "mob"->"mob-spawning";default->"explosions";};boolean value=toggle(args[2]);
                List<ClaimData> claims;
                if(args.length==4){if(!Set.of("all","tumu","tümü").contains(args[3])){send(p,"invalid-input");return;}claims=new ArrayList<>(plugin.getClaimManager().getAllClaims().values());}
                else{ClaimData claim=plugin.getClaimManager().getClaimAt(p.getLocation().getChunk());if(claim==null){send(p,"sell-not-claimed");return;}claims=List.of(claim);}
                for(ClaimData claim:claims)if(!plugin.getAccessManager().policy(claim,key).locked()&&!plugin.getTransactionManager().isLocked(claim.getChunkKey()))claim.setChoice(key,value);
                plugin.getClaimManager().saveAll();send(p,"operation-success");
            }
            case "reload","yenile" -> {try{plugin.reload();send(p,"reload-success");}catch(IllegalArgumentException ex){send(p,"reload-invalid");plugin.getLogger().warning(ex.getMessage());}}
            default -> help(p,true);
        }
    }
    private boolean toggle(String text){return switch(text.toLowerCase(Locale.ROOT)){case "on","enable","ac","aç"->true;case "off","disable","kapat"->false;default->throw new IllegalArgumentException("toggle");};}
    private void help(Player p,boolean admin) {
        MessageManager msg=plugin.getMessageManager();String root=admin?"admin-help-":"help-";
        p.sendMessage(msg.getMessage(p,root+"header",Map.of("{prefix}",msg.getPrefix())));
        for(String suffix:admin?List.of("toggle","delete","give","price","cooldown","prefix","economy","pvp","explosion","mob","bulksell","pricediff","lang","blacklist","reload","bypass","ban","unban","purge","transactions"):List.of("claim","sell","unclaim","see","info","list","pvp","explosion","mob","mobexplosion","villager","doors","trapdoors","trust","untrust","trustlist","trustperm","coowner-add","coowner-remove","transfer","market-list","market-cancel","market-buy","auto","name","setspawn","tp","warn","lang","confirm","cancel"))p.sendMessage(msg.getMessage(p,root+suffix));
        if(!admin&&p.hasPermission("dyclaim.admin"))p.sendMessage(msg.getMessage(p,"help-admin"));
        p.sendMessage(msg.getMessage(p,root+"footer"));
    }
    private record Suggestion(String en,String tr,String permission) {}
    private static final List<Suggestion> SUBS=List.of(
            new Suggestion("sell","sat","sell"),new Suggestion("see","gör","see"),new Suggestion("info","bilgi","info"),new Suggestion("list","liste","list"),
            new Suggestion("pvp","pvp","settings"),new Suggestion("explosion","patlama","settings"),new Suggestion("mob","mob","settings"),new Suggestion("villager","köylü","settings"),new Suggestion("doors","kapı","settings"),new Suggestion("trapdoors","tuzakkapısı","settings"),
            new Suggestion("mobexplosion","mobpatlama","settings"),new Suggestion("trust","güven","trust"),new Suggestion("untrust","güvensil","trust"),new Suggestion("trustlist","güvenliste","trust"),new Suggestion("trustperm","güvenizin","trust"),
            new Suggestion("coowner","ortak","coowner"),new Suggestion("transfer","devret","transfer"),new Suggestion("market","pazar","market"),new Suggestion("auto","otomatik","auto"),new Suggestion("name","isim","name"),new Suggestion("setspawn","spawnayarla","setspawn"),new Suggestion("tp","ışınlan","teleport"),new Suggestion("warn","uyar","warn"),new Suggestion("admin","admin","admin"),new Suggestion("lang","dil",""),new Suggestion("help","yardım",""));
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        String input=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        args=dev.dyclaim.util.CommandWords.normalize(args);
        boolean tr="tr".equals(plugin.getMessageManager().getPreferredLanguage(sender));List<String> options=new ArrayList<>();
        if(args.length==1){for(Suggestion s:SUBS)if(s.permission.isEmpty()||sender.hasPermission("dyclaim."+s.permission))options.add(tr?s.tr:s.en);}
        else if(args.length>=2) {
            String sub=args[0].toLowerCase(Locale.ROOT);Suggestion selected=SUBS.stream().filter(s->dev.dyclaim.util.CommandWords.matches(s.en,sub)||dev.dyclaim.util.CommandWords.matches(s.tr,sub)).findFirst().orElse(null);
            if(selected==null||(!selected.permission.isEmpty()&&!sender.hasPermission("dyclaim."+selected.permission)))return List.of();
            String en=selected.en;
            if(en.equals("admin")) {
                if(args.length==2)options.addAll(tr?List.of("aç","kapat","sil","ver","fiyat","bekleme","önek","ekonomi","pvp","patlama","mob","toplusat","farkver","dil","karaliste","yenile","koruma-atla","yasakla","yasakkaldir","temizle","islemler"):List.of("enable","disable","delete","give","price","cooldown","prefix","economy","pvp","explosion","mob","bulksell","pricediff","lang","blacklist","reload","bypass","ban","unban","purge","transactions"));
                else if(args.length==3){switch(args[1]){case "give","ver","delete","sil","ban","yasakla","unban","yasakkaldir","bulksell","toplusat"->Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));case "lang","dil"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"auto","en","tr"));case "blacklist","karaliste"->options.addAll(tr?List.of("ekle","çıkar"):dev.dyclaim.util.CommandWords.choices(tr,"add","remove"));case "pvp","explosion","patlama","mob","economy","ekonomi","bypass","koruma-atla"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"on","off"));default->{}}}
                else if(args.length==4){if(Set.of("delete","sil","pvp","explosion","patlama","mob").contains(args[1]))options.add(tr?"tümü":"all");if(Set.of("blacklist","karaliste").contains(args[1]))Bukkit.getWorlds().forEach(w->options.add(w.getName()));if(Set.of("ban","yasakla").contains(args[1]))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"30m","2h","7d","permanent"));}
            }else if(args.length==2){switch(en){case "trust","untrust","transfer","warn","trustperm"->Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));case "market"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"list","cancel","buy"));case "coowner"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"add","remove"));case "auto"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"on","off"));case "lang"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"auto","en","tr"));case "tp"->{if(sender instanceof Player p){List<ClaimData> claims=plugin.getClaimManager().getPlayerClaims(p.getUniqueId());for(int i=0;i<claims.size();i++){options.add(String.valueOf(i+1));if(claims.get(i).getName()!=null)options.add(claims.get(i).getName());}}}default->{}}}
            else if(args.length==3){if(en.equals("coowner")&&args[1].equals("add"))Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));if(en.equals("trust"))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"30m","2h","7d","permanent"));if(en.equals("trustperm"))options.addAll(dev.dyclaim.model.TrustGrant.RIGHTS.stream().map(word->dev.dyclaim.util.CommandWords.display(word,tr)).toList());}
            else if(args.length==4&&en.equals("trustperm"))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"allow","deny"));
        }

        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
    }
}



