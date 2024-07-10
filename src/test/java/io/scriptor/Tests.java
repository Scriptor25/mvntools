package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

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
        root.dumpTree();
    }

    @Test
    @DisplayName("Get Artifact + Export GraphViz Graph")
    public void testExportGraph() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        Graphviz.fromGraph(root.generateGraph())
                .height(1000)
                .render(Format.SVG)
                .toFile(new File("export.svg"));
    }

    @Test
    @DisplayName("Get Artifact + Read All Resources Recursively")
    public void testExtractResources() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        for (final var dep : root) {
            final var jar = dep.getJar();
            try (final var jarFile = new JarFile(jar)) {
                final var entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    final var entry = entries.nextElement();
                    if (!entry.isDirectory() && !entry.getName().endsWith(".class"))
                        System.out.println(entry.getName());
                }
            }
        }
    }
}
