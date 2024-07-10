package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

public class Tests {

    public static final String ID = "org.apache.maven:maven-core:3.9.8";

    @Test
    @DisplayName("Simple Artifact Get")
    public void testGetArtifact() throws IOException {
        final var artifact = MvnArtifact.getArtifact(ID);
        assertNotNull(artifact);
    }

    @Test
    @DisplayName("Simple Artifact Get + Dump Dependency Tree")
    public void testDumpTree() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        root.dumpTree();
    }

    @Test
    @DisplayName("Simple Artifact Get + Export GraphViz Graph")
    public void testExportGraph() throws IOException {
        final var root = MvnArtifact.getArtifact(ID);
        Graphviz.fromGraph(root.generateGraph())
                .height(1000)
                .render(Format.PNG)
                .toFile(new File("export.png"));
    }
}
