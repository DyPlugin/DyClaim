package dev.dyclaim.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Type;

/** No fallback to empty data: malformed files must stop writes. */
public final class AtomicJson {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private AtomicJson() {}
    public static <T> T read(Path path, Type type) throws IOException {
        try {
            T data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (data == null) throw new IOException("Empty/null JSON: " + path);
            return data;
        } catch (RuntimeException ex) { throw new IOException("Invalid JSON: " + path, ex); }
    }
    public static void write(Path path, Object data) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel out = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) out.write(buffer);
            out.force(true);
        }
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
