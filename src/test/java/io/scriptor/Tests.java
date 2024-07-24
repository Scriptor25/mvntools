package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class Tests {

    public static final String ID = "de.auctores.dev:patchmgr:0.0.1-SNAPSHOT";

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
    @DisplayName("Get Artifact + Read All Resources")
    public void testReadResources() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        for (final var entry : root.entries())
            if (!entry.isDirectory())
                System.out.println(entry.getName());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources + Get Stream For Entry")
    public void testGetEntryStream() throws MalformedURLException, IOException {
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
