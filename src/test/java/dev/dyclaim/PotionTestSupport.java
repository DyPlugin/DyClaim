package dev.dyclaim;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.potion.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Minimal effect registry for Paper API initialization outside a running server. */
final class PotionTestSupport {
    private static final Map<NamespacedKey,PotionEffectType> effects=new HashMap<>();
    static Object registry(Class<?> type) {
        return Proxy.newProxyInstance(Registry.class.getClassLoader(),new Class[]{Registry.class},(proxy,method,args)->{
            if(method.getName().equals("get")&&type==PotionEffectType.class)return effects.computeIfAbsent((NamespacedKey)args[0],key->new Effect(key,effects.size()+1));
            if(method.getName().equals("iterator"))return effects.values().iterator();
            if(method.getName().equals("toString"))return "TestEffectRegistry";
            return null;
        });
    }
    private static final class Effect extends PotionEffectType {
        private final NamespacedKey key;private final int id;
        Effect(NamespacedKey key,int id){this.key=key;this.id=id;}
        public NamespacedKey getKey(){return key;}
        public String getName(){return key.getKey().toUpperCase(Locale.ROOT);}
        public int getId(){return id;}
        public boolean isInstant(){return key.getKey().startsWith("instant_");}
        public Color getColor(){return Color.WHITE;}
        public double getDurationModifier(){return 1;}
        public PotionEffect createEffect(int duration,int amplifier){return new PotionEffect(this,duration,amplifier);}
        public Map<Attribute,AttributeModifier> getEffectAttributes(){return Map.of();}
        public double getAttributeModifierAmount(Attribute attribute,int amplifier){return 0;}
        public Category getEffectCategory(){return Category.NEUTRAL;}
        public String getTranslationKey(){return "effect."+key.getNamespace()+"."+key.getKey();}
        public String translationKey(){return getTranslationKey();}
    }
}
