package com.iiordanov.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class CustomClientConfigFileReaderTest {
    @Test
    public void shippedExamplePreservesMapShapeAndValueTypes() throws Exception {
        try (InputStream input = Files.newInputStream(
                Paths.get("src/main/assets/custom_vnc_client.yaml-EXAMPLE"))) {
            assertEquals(Map.of("mainConfiguration", Map.of(
                    "visibility", Map.of("connectionTypeLabel", 8),
                    "title", Map.of("default", "Default Alternate Title",
                            "en", "Override of Title for English Language"),
                    "subtitle", Map.of("default", "Default Alternate SubTitle",
                            "en", "Override of Sub Title for English Language"))),
                    new CustomClientConfigFileReader(input).getConfigData());
        }
    }

    @Test
    public void malformedYamlStillPropagatesParserFailure() {
        assertThrows(YAMLException.class, () -> read("mainConfiguration: [unterminated"));
    }

    @Test(timeout = 2000)
    public void recursiveMappingKeysAreRejectedByDefault() {
        assertThrows(YAMLException.class, () -> read("&root\n? *root\n: value\n"));
    }

    @Test(timeout = 2000)
    public void recursiveValuesRetainExistingAliasIdentity() {
        Map<String, ?> recursive = read("mainConfiguration:\n"
                + "  recursive: &recursive\n"
                + "    self: *recursive\n").get("mainConfiguration").get("recursive");
        assertSame(recursive, recursive.get("self"));
    }

    @Test(timeout = 2000)
    public void collectionAliasesKeepDefaultFiftyAliasLimit() {
        StringBuilder yaml = new StringBuilder("mainConfiguration:\n"
                + "  title: &title\n"
                + "    default: Default Title\n");
        for (int i = 0; i < 50; i++) {
            yaml.append("  alias").append(i).append(": *title\n");
        }
        Map<String, Map<String, ?>> configuration = read(yaml.toString()).get("mainConfiguration");
        assertSame(configuration.get("title"), configuration.get("alias49"));

        yaml.append("  alias50: *title\n");
        YAMLException failure = assertThrows(YAMLException.class, () -> read(yaml.toString()));
        assertTrue(failure.getMessage().contains("50"));
    }

    private static Map<String, Map<String, Map<String, ?>>> read(String yaml) {
        return new CustomClientConfigFileReader(new ByteArrayInputStream(
                yaml.getBytes(StandardCharsets.UTF_8))).getConfigData();
    }
}
