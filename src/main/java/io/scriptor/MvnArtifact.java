package io.scriptor;

import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import guru.nidi.graphviz.model.Graph;

/**
 * Representation of a maven artifact
 */
public class MvnArtifact implements Iterable<MvnArtifact> {

    /**
     * Cache for previously materialized artifacts, indexed by id
     * (groupId:artifactId:packaging:version)
     */
    private static final Map<String, MvnArtifact> ARTIFACTS = new HashMap<>();

    /**
     * Get or materialize an artifact by id.
     * 
     * @param id the artifact id (groupId:artifactId:packaging:version)
     * @return materialized artifact, or null if materialization fails
     * @throws IOException if any
     */
    public static MvnArtifact getArtifact(final String id) throws IOException {
        final var params = id.split(":");
        return getArtifact(
                params[0],
                params[1],
                params.length == 3 ? "jar" : params[2],
                params.length == 3 ? params[2] : params[3]);
    }

    /**
     * Get or materialize an artifact by id.
     * 
     * @param groupId    the groupId
     * @param artifactId the artifactId
     * @param packaging  the packaging
     * @param version    the version
     * @return materialized artifact, or null if materialization fails
     * @throws IOException if any
     */
    public static MvnArtifact getArtifact(
            final String groupId,
            final String artifactId,
            final String packaging,
            final String version)
            throws IOException {

        final var id = groupId + ':' + artifactId + ':' + version;
        if (ARTIFACTS.containsKey(id))
            return ARTIFACTS.get(id);

        MvnArtifact artifact = null;
        try {
            artifact = new MvnArtifact(groupId, artifactId, packaging, version);
        } catch (XmlPullParserException e) {
            System.err.printf(
                    "Failed to materialize '%s:%s:%s:%s': %s%n",
                    groupId,
                    artifactId,
                    packaging,
                    version,
                    e.getMessage());
        }

        ARTIFACTS.put(id, artifact);
        return artifact;
    }

    /**
     * Fetch a remote maven artifact into the local repository.
     * 
     * @param groupId    the groupId
     * @param artifactId the artifactId
     * @param packaging  the packaging
     * @param version    the version
     * @param transitive if not only the artifacts pom is required
     * @throws IOException if any
     */
    public static void fetchArtifact(
            final String groupId,
            final String artifactId,
            final String packaging,
            final String version,
            final boolean transitive)
            throws IOException {
        final var dir = new File(".");

        int mode = 0;
        try {
            Runtime.getRuntime().exec("mvn", null, dir).waitFor();
            mode = 1;
        } catch (IOException e1) {
            try {
                Runtime.getRuntime().exec("mvn.cmd", null, dir).waitFor();
                mode = 2;
            } catch (IOException e2) {
                throw e2;
            } catch (InterruptedException e) {
            }
        } catch (InterruptedException e) {
        }

        final String exec;
        switch (mode) {
            case 1: // mvn executable
                exec = "mvn";
                break;

            case 2: // mvn.cmd executable
                exec = "mvn.cmd";
                break;

            default: // no executable
                throw new IllegalStateException("No maven executable");
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
                .directory(dir);
        final var proc = procBuilder.start();

        try {
            final var code = proc.waitFor();
            if (code != 0) {
                throw new IllegalStateException(
                        String.format(
                                "Failed to fetch '%s:%s:%s:%s': Exit code %d",
                                groupId,
                                artifactId,
                                packaging,
                                version,
                                code));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
    private static String getProperty(final Map<String, String> properties, String key) {
        while (key != null && key.startsWith("${") && key.endsWith("}")) {
            key = properties.getOrDefault(key.substring(2, key.length() - 1), null);
        }
        return key;
    }

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
     * @throws IOException            if any or if no maven executable is found
     * @throws XmlPullParserException if any
     */
    private MvnArtifact(
            final String groupId,
            final String artifactId,
            final String packaging,
            final String version)
            throws XmlPullParserException, IOException {

        // generate a prefix for the artifact for later use
        mPrefix = String.format(
                "%2$s%1$s%3$s%1$s%4$s%1$s%3$s-%4$s",
                File.separator,
                groupId.replaceAll("\\.", "/"),
                artifactId,
                version);

        mPom = new File(MvnTools.getRepository(), mPrefix + ".pom");

        // if the pom file does not exist, i.e. the artifact is not yet in the local
        // repo, then fetch it from the remote
        if (!mPom.exists()) {
            fetchArtifact(groupId, artifactId, packaging, version, true);
        }

        // parse the pom file
        final var model = MvnTools.getModel(mPom);

        // preprocess the parent artifact, if it has one
        if (model.getParent() != null) {
            mParent = getArtifact(model.getParent().getId());
            if (mParent != null)
                mProperties.putAll(mParent.mProperties);
        } else {
            mParent = null;
        }

        // copy the properties from the model into the artifacts properties
        model.getProperties().forEach((key, value) -> mProperties.put((String) key, (String) value));

        // check for inherited groupid and version
        mGroupId = model.getGroupId() == null ? model.getParent().getGroupId() : model.getGroupId();
        mArtifactId = model.getArtifactId();
        mPackaging = model.getPackaging();
        mVersion = model.getVersion() == null ? model.getParent().getVersion() : model.getVersion();

        // define default properties that MUST be provided for the system to work 100%
        // (or at least 99.999%)
        mProperties.put("project.artifactId", mArtifactId);
        mProperties.put("project.groupId", mGroupId);
        mProperties.put("project.version", mVersion);

        // set versions and other properties from the dependency management part of the
        // model
        if (model.getDependencyManagement() != null) {
            for (final var dependency : model.getDependencyManagement().getDependencies()) {
                final var depGroupId = getProperty(mProperties, dependency.getGroupId());
                final var depArtifactId = getProperty(mProperties, dependency.getArtifactId());
                final var depType = getProperty(mProperties, dependency.getType());
                final var depVersion = getProperty(mProperties, dependency.getVersion());
                final var depScope = getProperty(mProperties, dependency.getScope());
                final var depOptional = getProperty(mProperties, dependency.getOptional());

                final var id = depGroupId + '$' + depArtifactId;
                mProperties.put(id + ".type", depType);
                mProperties.put(id + ".version", depVersion);
                mProperties.put(id + ".scope", depScope);
                mProperties.put(id + ".optional", depOptional);
            }
        }

        // retrieve all dependencies
        final List<MvnArtifact> dependencies = new Vector<>();
        for (final var dependency : model.getDependencies()) {
            final var depGroupId = getProperty(mProperties, dependency.getGroupId());
            final var depArtifactId = getProperty(mProperties, dependency.getArtifactId());
            final var id = depGroupId + '$' + depArtifactId;
            var depType = getProperty(mProperties, dependency.getType());
            var depVersion = getProperty(mProperties, dependency.getVersion());
            var depScope = getProperty(mProperties, dependency.getScope());
            var depOptional = getProperty(mProperties, dependency.getOptional());

            if (depType == null)
                depType = mProperties.get(id + ".type");
            if (depVersion == null)
                depVersion = mProperties.get(id + ".version");
            if (depScope == null)
                depScope = mProperties.get(id + ".scope");
            if (depOptional == null)
                depOptional = mProperties.get(id + ".optional");

            final var isOptional = depOptional == null ? false : Boolean.parseBoolean(depOptional);

            if (isOptional || (depScope != null && !"compile".equals(depScope)))
                continue;

            final var artifact = getArtifact(
                    depGroupId,
                    depArtifactId,
                    depType,
                    depVersion);
            if (artifact != null)
                dependencies.add(artifact);
        }

        // make the dependencies list an array
        mDependencies = dependencies.toArray(new MvnArtifact[0]);
    }

    @Override
    public String toString() {
        return getId();
    }

    public String getGroupId() {
        return mGroupId;
    }

    public String getArtifactId() {
        return mArtifactId;
    }

    public String getPackaging() {
        return mPackaging;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getId() {
        // e.g. io.scriptor:mvntools:jar:1.0.0
        return mGroupId + ':' + mArtifactId + ':' + mPackaging + ':' + mVersion;
    }

    public String getPrefix() {
        return mPrefix;
    }

    public File getPom() {
        return mPom;
    }

    public File getPackageFile() {
        return new File(MvnTools.getRepository(), mPrefix + "." + mPackaging);
    }

    public MvnArtifact getParent() {
        return mParent;
    }

    public MvnArtifact[] getDependencies() {
        return mDependencies;
    }

    /**
     * Open the artifacts jar.
     * 
     * @return the jarfile or null if the dependency is not a jar
     * @throws IOException if any
     */
    public JarFile openPackage() throws IOException {
        // only jar and war files can be unpacked using the java jar api
        if (!(mPackaging.equals("jar") || mPackaging.equals("war")))
            return null;

        // if the jar/war does not exist yet, maybe because maven is lazy, fetch it
        final var file = getPackageFile();
        if (!file.exists())
            fetchArtifact(mGroupId, mArtifactId, mPackaging, mVersion, false);

        return new JarFile(file);
    }

    /**
     * Create an iterable over all elements in the artifacts package.
     * 
     * @return the iterable
     */
    public Iterable<JarEntry> entries() {
        return () -> {
            try {
                final var pkg = openPackage();
                if (pkg != null)
                    return pkg.entries().asIterator();

                // there unfortunatly is no empty iterator (or so do i think)
                return new Iterator<JarEntry>() {

                    @Override
                    public boolean hasNext() {
                        return false;
                    }

                    @Override
                    public JarEntry next() {
                        throw new NoSuchElementException();
                    }

                };
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Open a stream over the elements inside the artifacts package.
     * 
     * @return the stream
     * @throws IOException if any
     */
    public Stream<JarEntry> stream() throws IOException {
        final var pkg = openPackage();
        if (pkg != null)
            return pkg.stream();

        // why is there an empty stream but no empty iterator?! (see the one method
        // above)
        return Stream.empty();
    }

    /**
     * Open an input stream from a jar entry. You get one from either using stream()
     * or entries().
     * 
     * @param entry the jar entry
     * @return an input stream to the entry
     * @throws MalformedURLException if any
     * @throws IOException           if any
     */
    public InputStream openEntry(final JarEntry entry) throws MalformedURLException, IOException {
        final var pkgFile = getPackageFile();
        final var name = entry.getName();

        // jar url, e.g. jar:file:/path/to/my/jar/myjar.jar!/my/entry/name
        final var url = new URL("jar:file:" + pkgFile.getCanonicalPath().replaceAll("\\\\", "/") + "!/" + name);
        return url.openStream();
    }

    /**
     * Dump a pretty formatted dependency tree, starting from this artifact.
     */
    public void dumpTree() {
        System.out.println(getId());
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].dumpTree(1, new Vector<>(), i == mDependencies.length - 1);
    }

    /**
     * Dump a pretty formatted dependency tree, starting from this artifact.
     * 
     * @param depth   the depth
     * @param wasLast a list of all previous depths, if they were the last
     *                dependency
     * @param last    if this artifact is the last dependency of the previous
     */
    private void dumpTree(final int depth, final List<Boolean> wasLast, final boolean last) {
        String spaces = "";
        for (int i = 1; i < depth; ++i)
            spaces += wasLast.get(i - 1) ? "   " : "|  ";
        spaces += last ? "\\- " : "+- ";
        wasLast.add(last);
        System.out.printf("%s%s%n", spaces, getId());
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].dumpTree(depth + 1, wasLast, i == mDependencies.length - 1);
        wasLast.remove(depth - 1);
    }

    /**
     * Generate a graphviz graph with all dependencies, starting from this artifact.
     * 
     * @return a graphviz graph
     */
    public Graph generateGraph() {
        return generateGraph(graph().directed());
    }

    /**
     * Generate a graphviz graph with all dependencies, starting from this artifact.
     * 
     * @param graph the graph
     * @return graph
     */
    private Graph generateGraph(Graph graph) {
        graph = graph.with(
                node(getId()).link(
                        (String[]) Arrays.stream(mDependencies)
                                .map(dep -> dep.getId())
                                .toArray(size -> new String[size])));
        for (final var dep : mDependencies)
            graph = dep.generateGraph(graph);
        return graph;
    }

    @Override
    public Iterator<MvnArtifact> iterator() {
        return new Iterator<MvnArtifact>() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < mDependencies.length;
            }

            @Override
            public MvnArtifact next() {
                if (!hasNext())
                    throw new NoSuchElementException(
                            String.format(
                                    "Index %d out of range [0;%d[",
                                    i,
                                    mDependencies.length));
                return mDependencies[i++];
            }
        };
    }
}
