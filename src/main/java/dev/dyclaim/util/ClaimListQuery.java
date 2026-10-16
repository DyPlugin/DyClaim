package dev.dyclaim.util;

import dev.dyclaim.model.ClaimData;
import java.util.*;

/** Filters presentation only; original indices remain the /claim tp indices. */
public record ClaimListQuery(int page,String name,String world,String sale) {
    public record Row(int number,ClaimData claim) {}
    public static ClaimListQuery parse(String[] args){
        int page=1,index=1;String name=null,world=null,sale="all";
        if(index<args.length&&args[index].matches("-?\\d+")){page=Integer.parseInt(args[index++]);if(page<1)throw new IllegalArgumentException("page");}
        Set<String> seen=new HashSet<>();
        while(index<args.length){
            String key=switch(fold(args[index++])){case "name","isim"->"name";case "world","dunya"->"world";case "sale","satis"->"sale";default->throw new IllegalArgumentException("filter");};
            if(index>=args.length||!seen.add(key))throw new IllegalArgumentException("filter value");
            String value=args[index++];if(value.isBlank())throw new IllegalArgumentException("filter value");
            switch(key){case "name"->name=value;case "world"->world=value;case "sale"->sale=switch(fold(value)){case "all","tumu"->"all";case "listed","satista"->"listed";case "unlisted","satista-degil"->"unlisted";default->throw new IllegalArgumentException("sale");};}
        }
        return new ClaimListQuery(page,name,world,sale);
    }
    public List<Row> select(List<ClaimData> claims){
        List<Row> result=new ArrayList<>();
        for(int i=0;i<claims.size();i++){
            ClaimData c=claims.get(i);
            if(name!=null&&(c.getName()==null||!fold(c.getName()).contains(fold(name))))continue;
            if(world!=null&&!c.getWorld().equalsIgnoreCase(world))continue;
            if(sale.equals("listed")&&c.getMarketPrice()==null||sale.equals("unlisted")&&c.getMarketPrice()!=null)continue;
            result.add(new Row(i+1,c));
        }
        return List.copyOf(result);
    }
    public static List<String> suggestions(String[] args,boolean tr,List<ClaimData> claims){
        if(args.length<2)return List.of();
        int index=1;if(args[index].matches("\\d+")&&args.length>2)index++;
        while(index<args.length-1){
            String key=fold(args[index++]);
            if(index==args.length-1)return switch(key){
                case "world","dunya"->claims.stream().map(ClaimData::getWorld).distinct().toList();
                case "name","isim"->claims.stream().map(ClaimData::getName).filter(Objects::nonNull).filter(name->!name.contains(" ")).distinct().toList();
                case "sale","satis"->tr?List.of("tümü","satışta","satışta-değil"):List.of("all","listed","unlisted");
                default->List.of();
            };
            index++;
        }
        return tr?List.of("isim","dünya","satış"):List.of("name","world","sale");
    }
    private static String fold(String text){return text.toLowerCase(Locale.ROOT).replace("i\u0307","i").replace('ı','i').replace('ş','s').replace('ç','c').replace('ğ','g').replace('ö','o').replace('ü','u');}
}
