package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class Tests {

    public static final String ID = "org.apache.maven:maven-core:3.9.8";

    public static void main(String[] args) throws IOException, XmlPullParserException, InterruptedException {
        new Tests().testDumpTree();
    }

    @Test
    @DisplayName("Get Artifact")
    public void testGetArtifact() throws IOException, XmlPullParserException, InterruptedException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
    }

    @Test
    @DisplayName("Get Artifact + Dump Dependency Tree")
    public void testDumpTree() throws IOException, XmlPullParserException, InterruptedException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        MvnTools.getLogger().info(() -> "%n%s".formatted(root.toTree()));
    }

    @Test
    @DisplayName("Get Artifact + Render Graph")
    public void testRenderGraph() throws IOException, XmlPullParserException, InterruptedException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        final var file = new File("export.svg");
        MvnTools.renderGraph(root, file);
        assertTrue(file.exists());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources")
    public void testReadResources() throws IOException, XmlPullParserException, InterruptedException {
        final var root = MvnArtifact.getArtifact(ID);
        assertNotNull(root);
        for (final var entry : root.entries())
            if (!entry.isDirectory())
                System.out.println(entry.getName());
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources + Get Stream For Entry")
    public void testGetEntryStream()
            throws MalformedURLException, IOException, XmlPullParserException, InterruptedException {
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
