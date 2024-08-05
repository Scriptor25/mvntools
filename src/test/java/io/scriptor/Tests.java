package io.scriptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(root.isComplete());
    }

    @Test
    @DisplayName("Get Artifact + Dump Dependency Tree")
    void testDumpTree() {
        final var root = MvnArtifact.getArtifact(ID);
        assertTrue(root.isComplete());
        MvnTools.getLogger().info(() -> "%n%s".formatted(root.toTree()));
    }
}
