package io.scriptor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MvnTools {

    public static void main(String[] args) throws IOException {
        final var root = MvnArtifact.getArtifact("io.scriptor:mvntools:1.0.0");
        root.dumpTree();
    }

    public static File getRepository() throws IOException {
        return RepositorySystem.defaultUserLocalRepository;
    }

    public static Model getModel(File pom) throws IOException, XmlPullParserException {
        final var reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);
        return reader.read(new FileReader(pom));
    }
}
