package io.scriptor;

import guru.nidi.graphviz.model.Graph;
import org.apache.maven.model.Dependency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static io.scriptor.MvnTools.fetchArtifact;

/**
 * Representation of a maven artifact
 */
public class MvnArtifact implements Iterable<MvnArtifact> {

    /**
     * Cache for previously materialized artifacts, indexed by id
     * (groupId:artifactId:packaging:version)
     */
    private static final Map<String, MvnArtifact> artifacts = new HashMap<>();

    // Tree chars
    private static final char VERTICAL = '|';
    private static final char UP_RIGHT = '\\';
    private static final char T_RIGHT = '+';
    private static final char HORIZONTAL = '-';

    private static final String ID_FORMAT = "%s:%s:%s:%s";
    private static final String JAR = "jar";
    private static final String COMPILE = "compile";
    private static final String FALSE = "false";

    /**
     * Get or materialize an artifact by id.
     *
     * @param id the artifact id (groupId:artifactId:packaging:version)
     * @return materialized artifact
     */
    @Nonnull
    public static MvnArtifact getArtifact(@Nonnull final String id) {
        final var params = id.split(":");
        final var groupId = params[0];
        final var artifactId = params[1];
        final String packaging;
        final String version;
        if (params.length == 3) {
            packaging = JAR;
            version = params[2];
        } else if (params.length == 4) {
            packaging = params[2];
            version = params[3];
        } else {
            MvnTools.getLogger()
                    .warning(() -> "Invalid artifact id '%s': missing version or too many parts".formatted(id));
            MvnTools.getLogger().warning(() -> "This causes the maven artifact to use the RELEASE meta version");
            packaging = JAR;
            version = "RELEASE";
        }
        return getArtifact(groupId, artifactId, packaging, version);
    }

    /**
     * Get or materialize an artifact by id.
     *
     * @param groupId    the groupId
     * @param artifactId the artifactId
     * @param packaging  the packaging
     * @param version    the version
     * @return materialized artifact
     */
    @Nonnull
    public static MvnArtifact getArtifact(
            @Nonnull final String groupId,
            @Nonnull final String artifactId,
            @Nonnull final String packaging,
            @Nonnull final String version) {

        final var fullId = ID_FORMAT.formatted(groupId, artifactId, packaging, version);
        MvnTools.getLogger().info(() -> "Get artifact %s".formatted(fullId));

        final var id = groupId + ':' + artifactId + ':' + version;
        if (artifacts.containsKey(id))
            return artifacts.get(id);

        MvnTools.getLogger().info(() -> "Materializing artifact");

        final var artifact = new MvnArtifact(groupId, artifactId, packaging, version);
        artifacts.put(id, artifact);
        return artifact;
    }

    /**
     * Get a property from a map of strings indexed by strings, but only if the key
     * is in format "${...}".
     * This also works recursively.
     *
     * @param properties the properties
     * @param key        the key
     * @param fallback   supplier for a fallback value
     * @return the value of the property or key if it is not a property
     */
    @Nonnull
    private static String getProperty(
            @Nonnull final Map<String, String> properties,
            @Nonnull String key,
            @Nonnull final Supplier<String> fallback) {
        while (key != null && key.startsWith("${") && key.endsWith("}"))
            key = properties.getOrDefault(key.substring(2, key.length() - 1), null);
        if (key != null)
            return key;
        return fallback.get();
    }

    /**
     * Get a property from a map of strings indexed by strings, but only if the key
     * is in format "${...}".
     * This also works recursively.
     *
     * @param properties the properties
     * @param key        the key
     * @return the value of the property or key if it is not a property
     */
    @Nonnull
    private static String getProperty(
            @Nonnull final Map<String, String> properties,
            @Nonnull String key) {
        return getProperty(properties, key, () -> "");
    }

    /**
     * Resolve the dependency
     *
     * @param props the properties
     * @param dep   the dependency model
     * @return the materialized dependency artifact, or null if optional or not
     * compile scope
     */
    @Nonnull
    private static Optional<MvnArtifact> resolveDependency(
            @Nonnull final Map<String, String> props,
            @Nonnull final Dependency dep) {
        final var depGroupId = getProperty(
                props,
                dep.getGroupId(),
                () -> "");
        final var depArtifactId = getProperty(
                props,
                dep.getArtifactId(),
                () -> "");
        final var id = depGroupId + '$' + depArtifactId;
        final var depPackaging = getProperty(
                props,
                dep.getType(),
                () -> props.getOrDefault(id + ".packaging", JAR));
        final var depVersion = getProperty(
                props,
                dep.getVersion(),
                () -> props.get(id + ".version"));
        final var depScope = getProperty(
                props,
                dep.getScope(),
                () -> props.getOrDefault(id + ".scope", COMPILE));
        final var depOptional = getProperty(
                props,
                dep.getOptional(),
                () -> props.getOrDefault(id + ".optional", FALSE));

        final var isOptional = Boolean.parseBoolean(depOptional);
        if (isOptional || !COMPILE.equals(depScope))
            return Optional.empty();

        return Optional.of(getArtifact(depGroupId, depArtifactId, depPackaging, depVersion));
    }

    private final boolean mComplete;
    private final String mGroupId;
    private final String mArtifactId;
    private final String mPackaging;
    private final String mVersion;
    private final String mPrefix;
    private final File mPom;
    private final MvnArtifact mParent;
    private final MvnArtifact[] mDependencies;
    private final Map<String, String> mProperties = new HashMap<>();

    /**
     * Materialize a new artifact.
     *
     * @param groupId    the groupId
     * @param artifactId the artifactId
     * @param packaging  the packaging
     * @param version    the version
     */
    private MvnArtifact(
            @Nonnull final String groupId,
            @Nonnull final String artifactId,
            @Nonnull final String packaging,
            @Nonnull String version) {

        if (version.matches("[\\[(][a-zA-Z0-9-_.]*,[a-zA-Z0-9-_.]*[])]")) {
            if (!fetchArtifact(groupId, artifactId, packaging, version, true)) {
                final var fullId = ID_FORMAT.formatted(groupId, artifactId, packaging, version);
                MvnTools.getLogger().warning(() -> "Generated incomplete artifact %s".formatted(fullId));
                mComplete = false;
                mGroupId = groupId;
                mArtifactId = artifactId;
                mPackaging = packaging;
                mVersion = version;
                mPrefix = null;
                mPom = null;
                mParent = null;
                mDependencies = new MvnArtifact[0];
                return;
            }

            final var artifactRoot = new File(
                    MvnTools.getRepository(),
                    groupId.replace('.', File.separatorChar) + File.separatorChar + artifactId);

            final var prefix = Arrays.stream(artifactRoot.listFiles())
                    .filter(File::isDirectory)
                    .filter(dir -> !new File(dir, ".lastUpdated").exists())
                    .map(File::getName)
                    .max(String::compareTo);

            if (prefix.isPresent())
                version = prefix.get();
        }

        // generate a prefix for the artifact for later use
        mPrefix = String.format(
                "%2$s%1$c%3$s%1$c%4$s%1$c%3$s-%4$s",
                File.separatorChar,
                groupId.replace('.', File.separatorChar),
                artifactId,
                version);

        mPom = new File(MvnTools.getRepository(), mPrefix + ".pom");

        // if the pom file does not exist, i.e. the artifact is not yet in the local
        // repo, then fetch it from the remote
        if (!mPom.exists() &&
                (new File(mPom.getPath(), ".lastUpdated").exists() ||
                        !fetchArtifact(groupId, artifactId, packaging, version, true))) {
            final var fullId = ID_FORMAT.formatted(groupId, artifactId, packaging, version);
            MvnTools.getLogger().warning(() -> "Generated incomplete artifact %s".formatted(fullId));
            mComplete = false;
            mGroupId = groupId;
            mArtifactId = artifactId;
            mPackaging = packaging;
            mVersion = version;
            mParent = null;
            mDependencies = new MvnArtifact[0];
            return;
        }

        mComplete = true;

        // parse the pom file
        final var model = MvnTools.getModel(mPom);

        // preprocess the parent artifact, if exists
        final var modelParent = model.getParent();
        if (modelParent != null) {
            mParent = getArtifact(modelParent.getId());
            mProperties.putAll(mParent.mProperties);
        } else {
            mParent = null;
        }

        // copy the properties from the model into the artifacts properties
        model.getProperties().forEach((key, value) -> mProperties.put((String) key, (String) value));

        // set artifact id and packaging (those must be provided by the model)
        mArtifactId = model.getArtifactId();
        mPackaging = model.getPackaging();

        // check for inherited group id and version
        final var subGroupId = modelParent != null
                ? modelParent.getGroupId()
                : null;
        mGroupId = model.getGroupId() != null
                ? model.getGroupId()
                : subGroupId;

        final var subVersion = modelParent != null
                ? modelParent.getVersion()
                : null;
        mVersion = model.getVersion() != null
                ? model.getVersion()
                : subVersion;

        // define default properties that MUST be provided for the system to work 100%
        // (or at least 99.999%)
        mProperties.put("project.artifactId", mArtifactId);
        mProperties.put("project.groupId", mGroupId);
        mProperties.put("project.version", mVersion);

        // set versions and other properties from the dependency management part of the
        // model
        if (model.getDependencyManagement() != null)
            model.getDependencyManagement()
                    .getDependencies()
                    .forEach(this::resolveDependencyManagement);

        // retrieve all dependencies
        mDependencies = model.getDependencies()
                .stream()
                .map(dep -> resolveDependency(mProperties, dep))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(MvnArtifact[]::new);
    }

    private void resolveDependencyManagement(@Nonnull final Dependency dependency) {
        final var depGroupId = getProperty(mProperties, dependency.getGroupId());
        final var depArtifactId = getProperty(mProperties, dependency.getArtifactId());
        final var depPackaging = getProperty(mProperties, dependency.getType(), () -> JAR);
        final var depVersion = getProperty(mProperties, dependency.getVersion());
        final var depScope = getProperty(mProperties, dependency.getScope(), () -> COMPILE);
        final var depOptional = getProperty(mProperties, dependency.getOptional(), () -> FALSE);

        if ("import".equals(depScope)) {
            final var imported = getArtifact(depGroupId, depArtifactId, depPackaging, depVersion);
            mProperties.putAll(imported.mProperties);
            return;
        }

        final var id = depGroupId + '$' + depArtifactId;
        mProperties.put(id + ".packaging", depPackaging);
        mProperties.put(id + ".version", depVersion);
        mProperties.put(id + ".scope", depScope);
        mProperties.put(id + ".optional", depOptional);
    }

    @Override
    @Nonnull
    public String toString() {
        return getId();
    }

    public boolean isComplete() {
        return mComplete;
    }

    @Nonnull
    public String getGroupId() {
        return mGroupId;
    }

    @Nonnull
    public String getArtifactId() {
        return mArtifactId;
    }

    @Nonnull
    public String getPackaging() {
        return mPackaging;
    }

    @Nonnull
    public String getVersion() {
        return mVersion;
    }

    @Nonnull
    public String getId() {
        // e.g. io.scriptor:mvntools:jar:1.0.0
        return mGroupId + ':' + mArtifactId + ':' + mPackaging + ':' + mVersion;
    }

    @Nullable
    public String getPrefix() {
        return mPrefix;
    }

    @Nullable
    public File getPom() {
        return mPom;
    }

    @Nonnull
    public File getPackageFile() {
        return new File(MvnTools.getRepository(), mPrefix + '.' + mPackaging);
    }

    @Nullable
    public MvnArtifact getParent() {
        return mParent;
    }

    @Nonnull
    public MvnArtifact[] getDependencies() {
        return mDependencies;
    }

    /**
     * Open the artifacts jar.
     *
     * @return the jar file
     * @throws IOException if any
     */
    @Nonnull
    public JarFile openPackage() throws IOException {
        // only jar and war files can be unpacked using the java jar api
        if (!(mPackaging.equals(JAR) || mPackaging.equals("war")))
            throw new IOException("'" + mPackaging + "' is not a jar package type");

        // if the jar/war does not exist yet, maybe because maven is lazy, fetch it
        final var file = getPackageFile();
        if (!file.exists())
            fetchArtifact(mGroupId, mArtifactId, mPackaging, mVersion, false);

        if (!file.exists())
            throw new FileNotFoundException(file.toString());

        return new JarFile(file);
    }

    /**
     * Create an iterable over all elements in the artifacts package.
     *
     * @return the iterable
     */
    @Nonnull
    public Iterable<JarEntry> entries() {
        return () -> {
            try {
                return openPackage().entries().asIterator();
            } catch (final IOException e) {
                MvnTools.getLogger().warning(e::getMessage);
                return Collections.emptyIterator();
            }
        };
    }

    /**
     * Open a stream over the elements inside the artifacts package.
     *
     * @return the stream
     */
    @Nonnull
    public Stream<JarEntry> stream() {
        try {
            return openPackage().stream();
        } catch (final IOException e) {
            MvnTools.getLogger().warning(e::getMessage);
            return Stream.empty();
        }
    }

    /**
     * Open an input stream from a jar entry. You get one from either using stream()
     * or entries().
     *
     * @param entry the jar entry
     * @return an input stream to the entry
     * @throws IOException if any
     */
    @Nonnull
    public InputStream openEntry(@Nonnull final JarEntry entry) throws IOException {
        final var pkgFile = getPackageFile();
        final var name = entry.getName();

        // jar url, e.g. jar:file:/path/to/my/jar/myjar.jar!/my/entry/name
        final var url = new URL("jar:file:" + pkgFile.getCanonicalPath().replace('\\', '/') + "!/" + name);
        return url.openStream();
    }

    /**
     * Create a pretty formatted string of the dependency tree, starting from this
     * artifact.
     */
    @Nonnull
    public String toTree() {
        final var builder = new StringBuilder();
        builder.append(getId()).append('\n');
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].toTree(builder, 1, new Vector<>(), i == mDependencies.length - 1);
        return builder.toString();
    }

    /**
     * Create a pretty formatted string of the dependency tree, starting from this
     * artifact.
     *
     * @param builder the string builder to put the tree into
     * @param depth   the depth
     * @param wasLast a list of all previous depths, if they were the last
     *                dependency
     * @param last    if this artifact is the last dependency of the previous
     */
    private void toTree(
            @Nonnull final StringBuilder builder,
            final int depth,
            @Nonnull final List<Boolean> wasLast,
            final boolean last) {
        for (int i = 1; i < depth; ++i)
            builder
                    .append(Boolean.TRUE.equals(wasLast.get(i - 1)) ? ' ' : VERTICAL)
                    .append("  ");
        builder
                .append(last ? UP_RIGHT : T_RIGHT)
                .append(HORIZONTAL)
                .append(' ')
                .append(getId())
                .append('\n');

        wasLast.add(last);
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].toTree(builder, depth + 1, wasLast, i == mDependencies.length - 1);
        wasLast.remove(depth - 1);
    }

    /**
     * Generate a graphviz graph with all dependencies, starting from this artifact.
     *
     * @return a graphviz graph
     */
    @Nonnull
    public Graph generateGraph() {
        return generateGraph(graph().directed());
    }

    /**
     * Generate a graphviz graph with all dependencies, starting from this artifact.
     *
     * @param graph the graph
     * @return graph
     */
    @Nonnull
    private Graph generateGraph(Graph graph) {
        graph = graph.with(
                node(getId()).link(
                        Arrays.stream(mDependencies)
                                .map(MvnArtifact::getId)
                                .toArray(String[]::new)));
        for (final var dep : mDependencies)
            graph = dep.generateGraph(graph);
        return graph;
    }

    @Override
    @Nonnull
    public Iterator<MvnArtifact> iterator() {
        return Arrays.stream(mDependencies).iterator();
    }
}
