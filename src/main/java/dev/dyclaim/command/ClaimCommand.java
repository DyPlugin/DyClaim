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
    private void send(CommandSender p,String key){p.sendMessage(plugin.getMessageManager().getPrefixed(p,key));}
    private static final Set<String> CONSOLE_ADMIN=Set.of("enable","disable","price","fiyat","pricediff","farkver","cooldown","prefix","economy","ekonomi","lang","dil","blacklist","karaliste","reload","yenile","diagnose","tani","tanı","import","aktar","help","yardim","yardım");
    static final int HELP_PAGE_SIZE=8;
    private static final List<String> PLAYER_HELP=List.of("claim","dyc","sell","unclaim","see","info","list","list-filters","pvp","explosion","mob","villager","doors","trapdoors","trust","trustperm","untrust","trustlist","coowner-add","coowner-remove","transfer","market-list","market-cancel","market-buy","auto","name","message","setspawn","tp","cltp","ban","unban","banlist","warn","lang","confirm","cancel");
    private static final List<String> ADMIN_HELP=List.of("dyca","toggle","delete","give","price","cooldown","prefix","economy","pvp","explosion","mob","bulksell","pricediff","lang","blacklist","reload","diagnose","bypass","ban","unban","purge","transactions","import");
    /** /dyca and /cltp are shortcuts that reuse the /claim admin and /claim tp paths unchanged. */
    static String[] rooted(Command command,String[] args) {
        String root=command==null?null:switch(command.getName().toLowerCase(Locale.ROOT)){case "dyca"->"admin";case "cltp"->"tp";default->null;};
        if(root==null)return args;
        String[] out=new String[args.length+1];out[0]=root;System.arraycopy(args,0,out,1,args.length);return out;
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        boolean adminShortcut=command!=null&&command.getName().equalsIgnoreCase("dyca");
        String root="/"+(label==null||label.isBlank()?"claim":label);
        String adminRoot=adminShortcut?root:root+" admin";
        args=dev.dyclaim.util.CommandWords.normalize(rooted(command,args));
        try {
            if(!(sender instanceof Player)) {
                if(args.length>0&&args[0].equalsIgnoreCase("admin"))admin(sender,args,adminRoot);
                else if(args.length>0&&Set.of("help","yardim","yardım").contains(args[0].toLowerCase(Locale.ROOT)))admin(sender,new String[]{"admin","help"},adminRoot);
                else send(sender,"player-only");
                return true;
            }
            Player p=(Player)sender;
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
                case "admin" -> admin(p,args,adminRoot);
                case "help","yardim","yardım" -> {if(args.length>2){send(p,"invalid-input");return true;}help(p,false,args.length<2?null:args[1],root);}
                default -> send(p,"unknown-command");
            }
        }catch(IllegalArgumentException ex){send(sender,"invalid-input");}
        catch(RuntimeException ex){plugin.getLogger().severe("Claim action failed: "+ex.getMessage());send(sender,"transaction-failed");}
        return true;
    }
    private void admin(CommandSender p,String[] args,String adminRoot) {
        if(!p.hasPermission("dyclaim.admin")){send(p,"no-permission");return;}
        if(args.length<2){help(p,true,null,adminRoot);return;}
        if(Set.of("help","yardim","yardım").contains(args[1].toLowerCase(Locale.ROOT))){if(args.length>3){send(p,"invalid-input");return;}help(p,true,args.length<3?null:args[2],adminRoot);return;}
        if(!(p instanceof Player)&&!CONSOLE_ADMIN.contains(args[1].toLowerCase(Locale.ROOT))){send(p,"player-only");return;}
        var cfg=plugin.getConfigManager();
        switch(args[1].toLowerCase(Locale.ROOT)) {
            case "import","aktar" -> importClaims(p,args);
            case "diagnose","tani","tanı" -> {if(args.length!=2){send(p,"invalid-input");return;}dev.dyclaim.manager.Diagnostics.send(plugin,p);}
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
            case "lang","dil" -> {if(args.length!=3||!("auto".equals(args[2])||dev.dyclaim.manager.MessageManager.LANGUAGES.contains(args[2]))){send(p,"invalid-input");return;}cfg.setLang(args[2]);plugin.getMessageManager().reload();send(p,"operation-success");}
            case "blacklist","karaliste" -> {
                if(args.length!=4){send(p,"admin-usage-blacklist");return;}String world=args[3];if(Bukkit.getWorld(world)==null){p.sendMessage(plugin.getMessageManager().getPrefixed(p,"admin-world-not-found",Map.of("{world}",world)));return;}
                switch(args[2]){case "add","ekle"->cfg.addBlacklistedWorld(world);case "remove","cikar","çıkar"->cfg.removeBlacklistedWorld(world);default->throw new IllegalArgumentException("blacklist");}send(p,"operation-success");
            }
            case "pvp","explosion","patlama","mob" -> {
                if(args.length<3||args.length>4){send(p,"invalid-input");return;}String key=switch(args[1]){case "pvp"->"pvp";case "mob"->"mob-spawning";default->"explosions";};boolean value=toggle(args[2]);
                List<ClaimData> claims;
                if(args.length==4){if(!Set.of("all","tumu","tümü").contains(args[3])){send(p,"invalid-input");return;}claims=new ArrayList<>(plugin.getClaimManager().getAllClaims().values());}
                else{ClaimData claim=plugin.getClaimManager().getClaimAt(((Player)p).getLocation().getChunk());if(claim==null){send(p,"sell-not-claimed");return;}claims=List.of(claim);}
                for(ClaimData claim:claims)if(!plugin.getAccessManager().policy(claim,key).locked()&&!plugin.getTransactionManager().isLocked(claim.getChunkKey()))claim.setChoice(key,value);
                plugin.getClaimManager().saveAll();send(p,"operation-success");
            }
            case "reload","yenile" -> {if(args.length!=2){send(p,"invalid-input");return;}try{plugin.reload();send(p,"reload-success");}catch(IllegalArgumentException ex){send(p,"reload-invalid");plugin.getLogger().warning(ex.getMessage());}}
            default -> help(p,true,null,adminRoot);
        }
    }
    /** Preview first; the same command with confirm creates the claims. Works from the console too. */
    private void importClaims(CommandSender p,String[] args) {
        if(args.length<3||args.length>4){send(p,"import-usage");return;}
        var importer=new dev.dyclaim.manager.ClaimImporter(plugin);
        var source=importer.source(args[2]);
        if(source==null){p.sendMessage(plugin.getMessageManager().getPrefixed(p,"import-unavailable",Map.of("{source}",args[2])));return;}
        boolean confirm=args.length==4&&Set.of("confirm","onayla").contains(args[3].toLowerCase(Locale.ROOT));
        if(args.length==4&&!confirm){send(p,"import-usage");return;}
        dev.dyclaim.manager.ClaimImporter.Plan plan;
        try{plan=importer.plan(source);}
        catch(IllegalStateException ex){plugin.getLogger().warning("Import from "+source.id()+" failed: "+ex.getMessage());send(p,"import-failed");return;}
        if(!confirm){
            // GriefPrevention also registers /claim, so the plugin-unique /dyclaim label is suggested.
            String command=(p instanceof Player?"/":"")+"dyclaim admin import "+source.id()+" confirm";
            p.sendMessage(plugin.getMessageManager().getPrefixed(p,"import-preview",Map.of("{source}",source.id(),"{claims}",String.valueOf(plan.sourceClaims()),"{chunks}",String.valueOf(plan.claims().size()),"{claimed}",String.valueOf(plan.alreadyClaimed()),"{blacklisted}",String.valueOf(plan.blacklisted()),"{conflicts}",String.valueOf(plan.conflicts()),"{command}",command)));
            return;
        }
        var result=importer.apply(plan);
        p.sendMessage(plugin.getMessageManager().getPrefixed(p,"import-complete",Map.of("{created}",String.valueOf(result.created()),"{skipped}",String.valueOf(result.skipped()),"{trusted}",String.valueOf(result.trusted()))));
    }
    private boolean toggle(String text){return switch(text.toLowerCase(Locale.ROOT)){case "on","enable","ac","aç"->true;case "off","disable","kapat"->false;default->throw new IllegalArgumentException("toggle");};}
    private List<String> helpRows(CommandSender p,boolean admin) {
        String root=admin?"admin-help-":"help-";List<String> rows=new ArrayList<>();
        for(String suffix:admin?ADMIN_HELP:PLAYER_HELP){
            if(suffix.equals("list-filters")&&!p.hasPermission("dyclaim.list"))continue;
            if(suffix.equals("cltp")&&!p.hasPermission("dyclaim.teleport"))continue;
            if(suffix.equals("message")&&(!p.hasPermission("dyclaim.message")||!plugin.getConfigManager().values().getBoolean("claim-messages.enabled")))continue;
            if(!admin&&Set.of("ban","unban","banlist").contains(suffix)&&(!p.hasPermission("dyclaim.ban")||!plugin.getConfigManager().values().getBoolean("claim-entry-bans.enabled")))continue;
            if(!(p instanceof Player)&&!Set.of("toggle","dyca").contains(suffix)&&!CONSOLE_ADMIN.contains(suffix))continue;
            rows.add(root+suffix);
        }
        if(!admin&&p.hasPermission("dyclaim.admin"))rows.add("help-admin");
        return rows;
    }
    /** Players get short pages; the console keeps the complete list because it has no chat window limit. */
    private void help(CommandSender p,boolean admin,String requestedPage,String command) {
        MessageManager msg=plugin.getMessageManager();String root=admin?"admin-help-":"help-";
        List<String> rows=helpRows(p,admin);
        int page=requestedPage==null?1:Integer.parseInt(requestedPage);
        if(page<1)throw new IllegalArgumentException("page");
        boolean paged=p instanceof Player;int size=paged?HELP_PAGE_SIZE:Math.max(1,rows.size());
        int pages=Math.max(1,(rows.size()+size-1)/size);page=Math.min(page,pages);
        p.sendMessage(msg.getMessage(p,root+"header",Map.of("{prefix}",msg.getPrefix())));
        for(String key:rows.subList((page-1)*size,Math.min(rows.size(),page*size)))p.sendMessage(msg.getMessage(p,key));
        if(pages>1){
            String next=command+" "+("tr".equals(msg.getPreferredLanguage(p))?"yardım":"help")+" "+(page+1);
            p.sendMessage(msg.getMessage(p,page<pages?"help-page-next":"help-page",Map.of("{page}",String.valueOf(page),"{pages}",String.valueOf(pages),"{command}",next)));
        }
        p.sendMessage(msg.getMessage(p,root+"footer"));
    }
    private record Suggestion(String en,String tr,String permission) {}
    private boolean adminLeafAllowed(CommandSender sender,String word) {
        if(Set.of("purge","temizle").contains(word))return sender.hasPermission("dyclaim.admin.purge");
        if(Set.of("bypass","koruma-atla").contains(word))return sender.hasPermission("dyclaim.admin.bypass");
        return true;
    }
    private static final List<Suggestion> SUBS=List.of(
            new Suggestion("sell","sat","sell"),new Suggestion("see","gör","see"),new Suggestion("info","bilgi","info"),new Suggestion("list","liste","list"),
            new Suggestion("pvp","pvp","settings"),new Suggestion("explosion","patlama","settings"),new Suggestion("mob","mob","settings"),new Suggestion("villager","köylü","settings"),new Suggestion("doors","kapı","settings"),new Suggestion("trapdoors","tuzakkapısı","settings"),
            new Suggestion("mobexplosion","mobpatlama","settings"),new Suggestion("trust","güven","trust"),new Suggestion("untrust","güvensil","trust"),new Suggestion("trustlist","güvenliste","trust"),new Suggestion("trustperm","güvenizin","trust"),
            new Suggestion("coowner","ortak","coowner"),new Suggestion("transfer","devret","transfer"),new Suggestion("market","pazar","market"),new Suggestion("auto","otomatik","auto"),new Suggestion("name","isim","name"),new Suggestion("message","mesaj","message"),new Suggestion("ban","yasakla","ban"),new Suggestion("unban","yasakkaldır","ban"),new Suggestion("banlist","yasaklılar","ban"),new Suggestion("setspawn","spawnayarla","setspawn"),new Suggestion("tp","ışınlan","teleport"),new Suggestion("warn","uyar","warn"),new Suggestion("admin","admin","admin"),new Suggestion("lang","dil",""),new Suggestion("help","yardım",""));
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        String input=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        args=dev.dyclaim.util.CommandWords.normalize(rooted(command,args));
        boolean tr="tr".equals(plugin.getMessageManager().getPreferredLanguage(sender));List<String> options=new ArrayList<>();
        if(args.length==1){for(Suggestion s:SUBS){
            if(Set.of("mobexplosion","trustperm").contains(s.en))continue;
            if(!(sender instanceof Player)&&(!Set.of("admin","help").contains(s.en)||!sender.hasPermission("dyclaim.admin")))continue;
            if(s.permission.isEmpty()||sender.hasPermission("dyclaim."+s.permission))options.add(tr?s.tr:s.en);
        }}
        else if(args.length>=2) {
            String sub=args[0].toLowerCase(Locale.ROOT);Suggestion selected=SUBS.stream().filter(s->dev.dyclaim.util.CommandWords.matches(s.en,sub)||dev.dyclaim.util.CommandWords.matches(s.tr,sub)).findFirst().orElse(null);
            if(selected==null||(!selected.permission.isEmpty()&&!sender.hasPermission("dyclaim."+selected.permission)))return List.of();
            String en=selected.en;
            if(!(sender instanceof Player)&&!en.equals("admin"))return List.of();
            if(en.equals("admin")) {
                if(!(sender instanceof Player)&&args.length>2&&!CONSOLE_ADMIN.contains(args[1]))return List.of();
                if(args.length>2&&!adminLeafAllowed(sender,args[1]))return List.of();
                if(args.length==2)options.addAll(tr?List.of("aç","kapat","sil","ver","fiyat","bekleme","önek","ekonomi","pvp","patlama","mob","toplusat","farkver","dil","karaliste","yenile","tanı","koruma-atla","yasakla","yasakkaldir","temizle","islemler","aktar","yardım"):List.of("enable","disable","delete","give","price","cooldown","prefix","economy","pvp","explosion","mob","bulksell","pricediff","lang","blacklist","reload","diagnose","bypass","ban","unban","purge","transactions","import","help"));
                else if(args.length==3){switch(args[1]){case "give","ver","delete","sil","ban","yasakla","unban","yasakkaldir","bulksell","toplusat"->Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));case "lang","dil"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"auto","en","tr","de","es","ru"));case "blacklist","karaliste"->options.addAll(tr?List.of("ekle","çıkar"):dev.dyclaim.util.CommandWords.choices(tr,"add","remove"));case "pvp","explosion","patlama","mob","economy","ekonomi","bypass","koruma-atla"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"on","off"));case "import","aktar"->options.addAll(dev.dyclaim.manager.ClaimImporter.sources());case "help","yardim","yardım"->{if(sender instanceof Player){int pages=(helpRows(sender,true).size()+HELP_PAGE_SIZE-1)/HELP_PAGE_SIZE;for(int i=1;i<=pages;i++)options.add(String.valueOf(i));}}default->{}}}
                else if(args.length==4){if(Set.of("delete","sil","pvp","explosion","patlama","mob").contains(args[1]))options.add(tr?"tümü":"all");if(Set.of("blacklist","karaliste").contains(args[1]))Bukkit.getWorlds().forEach(w->options.add(w.getName()));if(Set.of("import","aktar").contains(args[1]))options.add(tr?"onayla":"confirm");if(Set.of("ban","yasakla").contains(args[1]))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"30m","2h","7d","permanent"));}
                if(args.length==2)options.removeIf(word->!adminLeafAllowed(sender,word));
                if(!(sender instanceof Player)&&args.length==2)options.removeIf(word->!CONSOLE_ADMIN.contains(dev.dyclaim.util.CommandWords.normalize(new String[]{"admin",word})[1]));
            }else if(args.length==2){switch(en){case "trust","untrust","transfer","warn","trustperm","ban","unban"->Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));case "market"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"list","cancel","buy"));case "coowner"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"add","remove"));case "auto"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"on","off"));case "message"->options.add(tr?"temizle":"clear");case "lang"->options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"auto","en","tr","de","es","ru"));case "tp"->{if(sender instanceof Player p){List<ClaimData> claims=plugin.getClaimManager().getPlayerClaims(p.getUniqueId());for(int i=0;i<claims.size();i++){options.add(String.valueOf(i+1));if(claims.get(i).getName()!=null)options.add(claims.get(i).getName());}}}default->{}}}
            else if(args.length==3){if(en.equals("coowner")&&args[1].equals("add"))Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));if(Set.of("trust","ban").contains(en))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"30m","2h","7d","permanent"));if(Set.of("trust","trustperm").contains(en))options.addAll(dev.dyclaim.model.TrustGrant.RIGHTS.stream().map(word->dev.dyclaim.util.CommandWords.display(word,tr)).toList());}
            else if(args.length==4&&Set.of("trust","trustperm").contains(en)&&dev.dyclaim.model.TrustGrant.RIGHTS.contains(args[2]))options.addAll(dev.dyclaim.util.CommandWords.choices(tr,"allow","deny"));
            if(args.length==2&&en.equals("explosion"))options.add("mob");
            if(args.length==2&&en.equals("help")&&sender instanceof Player){int pages=(helpRows(sender,false).size()+HELP_PAGE_SIZE-1)/HELP_PAGE_SIZE;for(int i=1;i<=pages;i++)options.add(String.valueOf(i));}
            if(en.equals("list")&&sender instanceof Player p)options.addAll(dev.dyclaim.util.ClaimListQuery.suggestions(args,tr,plugin.getClaimManager().getPlayerClaims(p.getUniqueId())));
        }

        return options.stream().filter(s->dev.dyclaim.util.CommandWords.startsWith(s,input)).sorted().toList();
    }
}



