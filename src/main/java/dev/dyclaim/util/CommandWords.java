package dev.dyclaim.util;

import java.util.*;

/** Only grammar positions are translated; player names and claim names stay untouched. */
public final class CommandWords {
    private static final Map<String,String> TR=new LinkedHashMap<>();
    static {
        String[][] pairs={{"add","ekle"},{"remove","çıkar"},{"list","listele"},{"cancel","iptal"},{"buy","satınal"},{"on","aç"},{"off","kapat"},{"allow","izinver"},{"deny","reddet"},{"auto","otomatik"},{"permanent","kalıcı"},{"build","inşa"},{"containers","sandıklar"},{"doors","kapılar"},{"trapdoors","tuzakkapıları"},{"redstone","kızıltaş"},{"entities","varlıklar"},{"teleport","ışınlanma"},{"cooldown","bekleme"},{"prefix","önek"},{"all","tümü"}};
        for(String[] pair:pairs)TR.put(pair[0],pair[1]);
    }
    private static String plain(String text){return text.toLowerCase(Locale.ROOT).replace('ı','i').replace('ş','s').replace('ç','c').replace('ğ','g').replace('ö','o').replace('ü','u');}
    public static String canonical(String text){String low=text.toLowerCase(Locale.ROOT);for(var entry:TR.entrySet())if(plain(entry.getValue()).equals(plain(low)))return entry.getKey();return low;}
    public static boolean matches(String a,String b){return plain(a).equals(plain(b));}
    public static String display(String word,boolean tr){return tr?TR.getOrDefault(word,word):word;}
    public static List<String> choices(boolean tr,String... words){return Arrays.stream(words).map(word->display(word,tr)).toList();}
    public static String[] normalize(String[] input){
        String[] args=input.clone();if(args.length==0)return args;String root=plain(args[0]);
        if(Set.of("coowner","ortak","market","pazar","auto","otomatik","lang","dil").contains(root)&&args.length>1)args[1]=canonical(args[1]);
        if(Set.of("trust","guven").contains(root)&&args.length>2)args[2]=canonical(args[2]);
        if(Set.of("trustperm","guvenizin").contains(root)){if(args.length>2)args[2]=canonical(args[2]);if(args.length>3)args[3]=canonical(args[3]);}
        if(root.equals("admin")&&args.length>1){
            args[1]=canonical(args[1]);if(args[1].equals("on"))args[1]="enable";if(args[1].equals("off"))args[1]="disable";String sub=plain(args[1]);
            if(Set.of("lang","dil","blacklist","karaliste","pvp","explosion","patlama","mob","economy","ekonomi","bypass","koruma-atla").contains(sub)&&args.length>2)args[2]=canonical(args[2]);
            if(Set.of("delete","sil","pvp","explosion","patlama","mob","ban","yasakla").contains(sub)&&args.length>3)args[3]=canonical(args[3]);
        }
        return args;
    }
}

