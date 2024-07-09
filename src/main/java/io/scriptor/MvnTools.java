package io.scriptor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MvnTools {

    public static File getRepository() throws IOException {
        final var home = System.getProperty("user.home");
        return new File(home, ".m2/repository").getCanonicalFile();
    }

    public static Model getModel(File pom) throws IOException, XmlPullParserException {
        final var reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);
        return reader.read(new FileReader(pom));
    }
}
