package io.scriptor;

import java.io.File;
import java.io.IOException;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

public class Test {

    public static void main(String[] args) throws IOException {
        final var root = MvnArtifact.getArtifact("io.scriptor:mvntools:1.0.0");
        root.dumpTree();
        Graphviz.fromGraph(root.generateGraph()).height(1000).render(Format.PNG).toFile(new File("export.png"));
    }
}
