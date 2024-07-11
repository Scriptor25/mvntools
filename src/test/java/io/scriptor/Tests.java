package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class Tests {

    public static final String ID = "io.scriptor:mvntools:1.0.0";

    @Test
    @DisplayName("Get Artifact")
    public void testGetArtifact() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
    }

    @Test
    @DisplayName("Get Artifact + Dump Dependency Tree")
    public void testDumpTree() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        root.dumpTree();
    }

    @Test
    @DisplayName("Get Artifact + Render Graph")
    public void testRenderGraph() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        final var file = new File("export.svg");
        MvnTools.renderGraph(root, file);
        assertTrue(file.exists());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources Recursively")
    public void testReadResources() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        for (final var dep : root) {
            for (final var entry : dep.getJarIterable()) {
                if (!entry.isDirectory())
                    System.out.println(entry.getName());
            }
        }
    }
}
