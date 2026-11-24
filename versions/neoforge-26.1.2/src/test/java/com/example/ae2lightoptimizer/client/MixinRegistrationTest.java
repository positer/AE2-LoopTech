package com.example.ae2lightoptimizer.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the crash guard actually shipped. The guard lives in a mixin, so a build can pass every other test while the
 * produced JAR carries no registration and the fix silently does nothing; this test fails in that case.
 */
class MixinRegistrationTest {
    private static List<String> clientMixins() throws Exception {
        try (InputStream in = MixinRegistrationTest.class.getResourceAsStream("/ae2lightoptimizer.mixins.json")) {
            assertNotNull(in, "ae2lightoptimizer.mixins.json must be on the classpath");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray client = root.getAsJsonArray("client");
            assertNotNull(client, "mixins.json must declare a client list");
            List<String> names = new ArrayList<>();
            client.forEach(entry -> names.add(entry.getAsString()));
            return names;
        }
    }

    @Test
    void registersTheOversizedItemViewportGuard() throws Exception {
        List<String> client = clientMixins();
        assertTrue(client.contains("OversizedItemViewportMixin"), "client mixins: " + client);
        assertNotNull(
                MixinRegistrationTest.class.getResource("/com/example/ae2lightoptimizer/mixin/OversizedItemViewportMixin.class"),
                "the registered mixin class must be compiled into the output");
    }

    @Test
    void doesNotKeepTheRetiredFactoryEditorRegistration() throws Exception {
        List<String> client = clientMixins();
        assertFalse(client.contains("FactoryEditorOversizedItemMixin"), "client mixins: " + client);
    }
}
