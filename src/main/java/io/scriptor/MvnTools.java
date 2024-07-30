package io.scriptor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

/**
 * The mvntools api class
 */
public class MvnTools {

    private static final Logger logger = Logger.getLogger("io.scriptor");

    static {
        final var handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {

            @Override
            public String format(final LogRecord rec) {
                return "[%s][%s]%n%s%n".formatted(new Date(rec.getMillis()), rec.getLevel(), rec.getMessage());
            }
        });

        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    public static Logger getLogger() {
        return logger;
    }

    /**
     * Get the default maven repository for the current user.
     * 
     * @return File pointing to the maven repository
     */
    public static File getRepository() {
        final var home = System.getProperty("user.home");
        return new File(home, ".m2" + File.separator + "repository");
    }

    /**
     * Read in the pom file and convert the output to a pom-like data structure.
     * 
     * @param pom the pom file
     * @return Model containing the read-in pom
     * @throws IOException            if the file does not exist, is a directory
     *                                rather than a regular file, or for some other
     *                                reason cannot be opened for reading.
     * @throws XmlPullParserException if any
     */
    public static Model getModel(final File pom) throws IOException, XmlPullParserException {
        final var reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);
        return reader.read(new FileReader(pom));
    }

    /**
     * Generate, render and export a GraphViz graph for the given artifact into a
     * file.
     * 
     * @param artifact the artifact
     * @param file     the output file
     * @throws IOException if any
     */
    public static void renderGraph(final MvnArtifact artifact, final File file) throws IOException {
        Graphviz.fromGraph(artifact.generateGraph())
                .render(Format.SVG)
                .toFile(file);
    }

    private MvnTools() {
    }
}
