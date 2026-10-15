package dev.dyclaim.platform.proxy;
public final class BridgeVersion {
    private static final String VERSION=read();
    private BridgeVersion(){}
    public static String get(){return VERSION;}
    private static String read(){
        try(var input=BridgeVersion.class.getResourceAsStream("/dyclaim-bridge-version.properties")){
            if(input==null)throw new IllegalStateException("Bridge version resource missing");
            java.util.Properties properties=new java.util.Properties();properties.load(input);String version=properties.getProperty("version");
            if(version==null||!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))throw new IllegalStateException("Invalid bridge version resource");return version;
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read bridge version",e);}
    }
}
