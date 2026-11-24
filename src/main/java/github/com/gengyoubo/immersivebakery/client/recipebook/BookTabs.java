/*
 * Taken from Hands-On Delight (https://github.com/Pocky-l/handson-delight), Copyright (c) 2026 Pocky, MIT License:
 * see META-INF/licenses/hands-on-delight.txt. Changed: its file and where that lies.
 */
package github.com.gengyoubo.immersivebakery.client.recipebook;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What the player made of the recipe book, kept on the client between games: which tab they last chose, per station type (the board
 * opens on its dishes for a player who only wants those), and the size and place they gave the book, the same at every station.
 */
final class BookTabs {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = ImmersiveBakery.MOD_ID + "-book.properties";
    private static Properties chosen;

    private BookTabs() {
    }

    /** The tab chosen for {@code book}, or null for all of it. */
    static synchronized String get(String book) {
        return values().getProperty(book);
    }

    static synchronized void put(String book, String tab) {
        Properties values = values();
        if (tab == null) {
            values.remove(book);
        } else {
            values.setProperty(book, tab);
        }
        write(values);
    }

    /** A number kept under {@code key}, or {@code fallback} when there is none or it is not a number. */
    static synchronized float number(String key, float fallback) {
        String value = values().getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            float number = Float.parseFloat(value);
            return Float.isFinite(number) ? number : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** Keeps {@code numbers[i]} under {@code keys[i]}; null removes the key. */
    static synchronized void putNumbers(String[] keys, Float[] numbers) {
        Properties values = values();
        for (int i = 0; i < keys.length; i++) {
            if (numbers[i] == null) {
                values.remove(keys[i]);
            } else {
                values.setProperty(keys[i], Float.toString(numbers[i]));
            }
        }
        write(values);
    }

    private static void write(Properties values) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                values.store(writer, "The recipe book: the tab each station's book opens on, its size and place; written by the game.");
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private static Properties values() {
        if (chosen == null) {
            chosen = new Properties();
            Path path = path();
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    chosen.load(reader);
                } catch (IOException | IllegalArgumentException exception) {
                    LOGGER.warn("Could not read {}", path, exception);
                }
            }
        }
        return chosen;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }
}
