package dev.dyclaim;
import dev.dyclaim.manager.EconomyManager;
import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import static org.junit.jupiter.api.Assertions.*;

class AbsentVaultRuntimeTest extends ManagerFixture {
    @Test void optionalVaultClassesAreNotRequiredToConstructOrUseDisabledEconomy() throws Exception {
        String manager=EconomyManager.class.getName();
        try(var loader=new URLClassLoader(new java.net.URL[]{EconomyManager.class.getProtectionDomain().getCodeSource().getLocation()},getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("net.milkbowl.vault."))throw new ClassNotFoundException("Vault deliberately absent");
                if(name.equals(manager)){Class<?> loaded=findLoadedClass(name);if(loaded==null)loaded=findClass(name);if(resolve)resolveClass(loaded);return loaded;}
                return super.loadClass(name,resolve);
            }
        }){
            Class<?> isolated=loader.loadClass(manager);
            Object instance=assertDoesNotThrow(()->isolated.getConstructor(DyClaim.class).newInstance(plugin));
            assertEquals(false,isolated.getMethod("isAvailable").invoke(instance));
            assertEquals(false,isolated.getMethod("isEnabled").invoke(instance));
            assertEquals("12.50",isolated.getMethod("formatMoney",double.class).invoke(instance,12.5));
        }
    }
}
