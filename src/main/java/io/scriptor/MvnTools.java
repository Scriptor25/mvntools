package io.scriptor;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The mvntools api class
 */
public class MvnTools {

    private static final Logger logger = Logger.getLogger("io.scriptor");
    private static final String ID_FORMAT = "%s:%s:%s:%s";

    static {
        final var handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {

            @Override
            @Nonnull
            public String format(final LogRecord rec) {
                return "[%s][%s] %s%n".formatted(new Date(rec.getMillis()), rec.getLevel(), rec.getMessage());
            }
        });

        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    @Nonnull
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Get the default maven repository for the current user.
     *
     * @return File pointing to the maven repository
     */
    @Nonnull
    public static File getRepository() {
        final var home = System.getProperty("user.home");
        return new File(home, ".m2" + File.separator + "repository");
    }

    /**
     * Read in the pom file and convert the output to a pom-like data structure.
     *
     * @param pom the pom file
     * @return Model containing the read-in pom
     */
    @Nonnull
    public static Model getModel(@Nonnull final File pom) {
        final var reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);
        try {
            return reader.read(new FileReader(pom));
        } catch (final XmlPullParserException | IOException e) {
            getLogger().warning(() -> "Failed to get model for %s: %s".formatted(pom, e));
            return new Model();
        }
    }

    /**
     * Read in the pom file and convert the output to a pom-like data structure.
     *
     * @param groupId    the group id
     * @param artifactId the artifact id
     * @param version    the version
     * @return Model containing the read-in pom
     */
    @Nonnull
    public static Model getModel(@Nonnull final String groupId, @Nonnull final String artifactId, @Nonnull final String version) {
        final var prefix = String.format(
                "%2$s%1$c%3$s%1$c%4$s%1$c%3$s-%4$s",
                File.separatorChar,
                groupId.replace('.', File.separatorChar),
                artifactId,
                version);

        final var pom = new File(getRepository(), prefix + ".pom");
        return getModel(pom);
    }

    /**
     * Fetch a remote maven artifact into the local repository.
     *
     * @param groupId    the groupId
     * @param artifactId the artifactId
     * @param packaging  the packaging
     * @param version    the version
     * @param transitive if not only the artifacts pom is required
     */
    public static boolean fetchArtifact(
            @Nonnull final String groupId,
            @Nonnull final String artifactId,
            @Nonnull final String packaging,
            @Nonnull final String version,
            final boolean transitive) {

        final var fullId = ID_FORMAT.formatted(groupId, artifactId, packaging, version);
        getLogger().info(() -> "Fetching artifact %s".formatted(fullId));

        final var cwd = new File(".");

        String exec;
        try {
            Runtime.getRuntime().exec("mvn", null, cwd).waitFor();
            exec = "mvn";
        } catch (final IOException a) {
            try {
                Runtime.getRuntime().exec("mvn.cmd", null, cwd).waitFor();
                exec = "mvn.cmd";
            } catch (final IOException b) {
                getLogger().warning("no suitable maven executable found");
                return false;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        final var procBuilder = new ProcessBuilder(
                exec,
                "dependency:get",
                "-DgroupId=" + groupId,
                "-DartifactId=" + artifactId,
                "-Dpackaging=" + packaging,
                "-Dversion=" + version,
                "-Dtransitive=" + transitive)
                .inheritIO()
                .directory(cwd);

        final int code;
        try {
            code = procBuilder.start().waitFor();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (final IOException e) {
            getLogger().warning(e::getMessage);
            return false;
        }

        if (code != 0) {
            getLogger().warning(() -> "Failed to fetch artifact %s: Exit code %d".formatted(fullId, code));
            return false;
        }

        return true;
    }

    /**
     * Generate, render and export a GraphViz graph for the given artifact into a
     * file.
     *
     * @param artifact the artifact
     * @param file     the output file
     * @throws IOException if any
     */
    public static void renderGraph(@Nonnull final MvnArtifact artifact, @Nonnull final File file) throws IOException {
        Graphviz.fromGraph(artifact.generateGraph())
                .render(Format.SVG)
                .toFile(file);
    }

    private MvnTools() {
    }
}
