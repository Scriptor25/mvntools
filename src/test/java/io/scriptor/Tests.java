package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class Tests {

    public static final String ID = "org.apache.maven:maven-core:3.9.8";

    public static void main(String[] args) {
        new Tests().testDumpTree();
    }

    @Test
    @DisplayName("Get Artifact")
    void testGetArtifact() {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
    }

    @Test
    @DisplayName("Get Artifact + Dump Dependency Tree")
    void testDumpTree() {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        MvnTools.getLogger().info(() -> "%n%s".formatted(root.toTree()));
    }

    @Test
    @DisplayName("Get Artifact + Render Graph")
    void testRenderGraph() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        final var file = new File("export.svg");
        MvnTools.renderGraph(root, file);
        assertTrue(file.exists());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources")
    void testReadResources() {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        for (final var entry : root.entries())
            if (!entry.isDirectory())
                System.out.println(entry.getName());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources + Get Stream For Entry")
    void testGetEntryStream() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        for (final var entry : root.entries())
            if (!entry.isDirectory() && !entry.getName().endsWith(".class")) {
                try (final var stream = root.openEntry(entry)) {
                    stream.transferTo(System.out);
                    System.out.flush();
                }
            }
    }
}
