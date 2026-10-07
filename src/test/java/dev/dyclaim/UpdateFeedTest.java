package dev.dyclaim;

import com.sun.net.httpserver.HttpServer;
import dev.dyclaim.util.UpdateFeed;
import dev.dyclaim.manager.UpdateChecker;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class UpdateFeedTest {
    private String version(String id,String number,String status,String channel,String game,String loader) {
        return "{\"id\":\""+id+"\",\"version_number\":\""+number+"\",\"status\":\""+status
                +"\",\"version_type\":\""+channel+"\",\"game_versions\":[\""+game+"\"],\"loaders\":[\""+loader+"\"]}";
    }
    @Test void normalizesLegacyAndModernBukkitVersions() {
        assertEquals("1.20.4",UpdateFeed.gameVersion("1.20.4-R0.1-SNAPSHOT"));
        assertEquals("26.2",UpdateFeed.gameVersion("26.2.build.2633-stable"));
        assertEquals("26.3",UpdateFeed.gameVersion("26.3-R0.1-SNAPSHOT"));
        assertEquals("26.3.1",UpdateFeed.gameVersion("26.3.1.build.1-stable"));
        assertThrows(IllegalArgumentException.class,()->UpdateFeed.gameVersion("unknown"));
    }
    @Test void encodesJsonFiltersWithoutRawQuotesOrBrackets() throws Exception {
        String url=UpdateFeed.requestUrl("26.2").toString();
        assertFalse(url.contains("["));assertFalse(url.contains("\""));
        String query=URLDecoder.decode(UpdateFeed.requestUrl("26.2").getQuery(),StandardCharsets.UTF_8);
        assertTrue(query.contains("game_versions=[\"26.2\"]"));assertTrue(query.contains("\"spigot\""));
        assertTrue(query.contains("include_changelog=false"));assertFalse(query.contains("limit="));
    }
    @Test void selectsHighestCompatiblePublicStableRegardlessOfResponseOrder() {
        String json="["+version("Old","1.0.0","listed","release","26.2","paper")+","+
                version("New","v1.0.2","listed","release","26.2","purpur")+","+
                version("Middle","1.0.1","listed","release","26.2","spigot")+"]";
        var release=UpdateFeed.select(json,"26.2","1.0.0-Beta");
        assertEquals("v1.0.2",release.version());assertTrue(release.url().endsWith("/New"));
        assertEquals("DyClaim-v1.0.2",UpdateChecker.displayVersion(release.version()));
    }
    @Test void ignoresUnpublishedPrereleaseAndIncompatibleEntries() {
        String json="["+version("A","1.0.9","draft","release","26.2","paper")+","+
                version("B","1.0.8","scheduled","release","26.2","paper")+","+
                version("C","1.0.7-Beta","listed","release","26.2","paper")+","+
                version("D","1.0.6","listed","beta","26.2","paper")+","+
                version("E","1.0.5","listed","release","26.3","paper")+","+
                version("F","1.0.4","listed","release","26.2","fabric")+"]";
        assertNull(UpdateFeed.select(json,"26.2","1.0.0"));
    }
    @Test void malformedEntryDoesNotHideValidReleaseAndEqualVersionIsNotUpdate() {
        String valid=version("Good","1.0.1","listed","release","1.20.4","bukkit");
        assertEquals("1.0.1",UpdateFeed.select("[{},null,"+valid+"]","1.20.4","1.0.0").version());
        assertNull(UpdateFeed.select("["+valid+"]","1.20.4","1.0.1"));
        assertThrows(RuntimeException.class,()->UpdateFeed.select("not JSON","1.20.4","1.0.0"));
    }
    @Test void actualJavaHttpTransportHandlesUtf8AndReportsHttpErrors() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/ok",exchange->{byte[] body="[\"Türkçe\"]".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});
        server.createContext("/fail",exchange->{exchange.sendResponseHeaders(429,-1);exchange.close();});
        server.start();
        try {
            String base="http://localhost:"+server.getAddress().getPort();
            assertEquals("[\"Türkçe\"]",UpdateFeed.fetch(new URL(base+"/ok"),"1.0.1"));
            IOException error=assertThrows(IOException.class,()->UpdateFeed.fetch(new URL(base+"/fail"),"1.0.1"));
            assertTrue(error.getMessage().contains("429"));
        } finally {server.stop(0);}
    }
}
