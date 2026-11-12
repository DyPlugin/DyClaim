package dev.dyclaim.manager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Only loaded when the server ships Adventure (Paper, Purpur, Folia); see {@link MessageManager#colorize}. */
final class MiniMessageBridge {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private MiniMessageBridge() {
    }

    /** Same shape of web address that Bukkit turns into a link when it parses legacy text. */
    private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile("[-\\w_.]{2,}\\.[a-z]{2,4}");

    static String toLegacy(String text) {
        return LEGACY.serialize(MINI_MESSAGE.deserialize(text));
    }

    /**
     * Finished legacy text as a component, or null when the server's own text path must stay: Bukkit splits line
     * breaks into separate messages and makes web addresses clickable.
     */
    static Object component(String legacyText) {
        if (legacyText.indexOf('\n') >= 0 || LINK.matcher(legacyText).find()) return null;
        return LEGACY.deserialize(legacyText);
    }

    /** False when the receiver is not an Adventure audience; the caller then sends the text. */
    static boolean send(Object receiver, Object component) {
        if (!(receiver instanceof Audience audience)) return false;
        audience.sendMessage((Component) component);
        return true;
    }
}
