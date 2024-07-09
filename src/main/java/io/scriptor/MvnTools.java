package io.scriptor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * The mvntools api class
 */
public class MvnTools {

    private MvnTools() {
    }

    /**
     * Get the default maven repository for the current user.
     * 
     * @return File pointing to the maven repository
     * @throws IOException if any
     */
    public static File getRepository() throws IOException {
        final var home = System.getProperty("user.home");
        return new File(home, ".m2/repository").getCanonicalFile();
    }

    /**
     * Read in the pom file and convert the output to a pom-like data structure.
     * 
     * @param pom the pom file
     * @return Model containing the read-in pom
     * @throws IOException            if any
     * @throws XmlPullParserException if any
     */
    public static Model getModel(final File pom) throws IOException, XmlPullParserException {
        final var reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);
        return reader.read(new FileReader(pom));
    }
}
