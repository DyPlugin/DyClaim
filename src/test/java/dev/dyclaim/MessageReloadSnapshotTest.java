package dev.dyclaim;
import dev.dyclaim.manager.MessageManager;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageReloadSnapshotTest extends ManagerFixture {
    @Test void readersKeepBothPreviousLanguagesUntilReloadHasFinished() throws Exception {
        Files.writeString(dir.resolve("messages_en.yml"),"sample: old English\n");
        Files.writeString(dir.resolve("messages_tr.yml"),"sample: Eski Türkçe\n");
        when(config.getLang()).thenReturn("en");MessageManager messages=new MessageManager(plugin);
        Files.writeString(dir.resolve("messages_en.yml"),"sample: new English\n");
        Files.writeString(dir.resolve("messages_tr.yml"),"sample: Yeni Türkçe\n");
        List<String> observed=new ArrayList<>();
        when(plugin.getResource("messages_tr.yml")).thenAnswer(i->{
            observed.add(messages.getRawMessage("sample"));when(config.getLang()).thenReturn("tr");
            observed.add(messages.getRawMessage("sample"));when(config.getLang()).thenReturn("en");return null;
        });
        messages.reload();assertEquals(List.of("old English","Eski Türkçe"),observed);
        assertEquals("new English",messages.getRawMessage("sample"));when(config.getLang()).thenReturn("tr");
        assertEquals("Yeni Türkçe",messages.getRawMessage("sample"));
    }
}
