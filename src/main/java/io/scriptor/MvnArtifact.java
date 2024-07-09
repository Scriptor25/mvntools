package io.scriptor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MvnArtifact {

    private static final Map<String, MvnArtifact> ARTIFACTS = new HashMap<>();

    public static MvnArtifact getArtifact(final String id)
            throws IOException {
        final var params = id.split(":");
        return getArtifact(
                params[0],
                params[1],
                params.length == 3 ? "jar" : params[2],
                params.length == 3 ? params[2] : params[3]);
    }

    public static MvnArtifact getArtifact(
            final String groupId,
            final String artifactId,
            final String type,
            final String version)
            throws IOException {
        final var id = groupId + ':' + artifactId + ':' + version;
        if (ARTIFACTS.containsKey(id))
            return ARTIFACTS.get(id);

        MvnArtifact artifact = null;
        try {
            artifact = new MvnArtifact(groupId, artifactId, type, version);
        } catch (XmlPullParserException e) {
            System.err.printf(
                    "Failed to materialize '%s:%s:%s:%s': %s%n",
                    groupId,
                    artifactId,
                    type,
                    version,
                    e.getMessage());
        }

        ARTIFACTS.put(id, artifact);
        return artifact;
    }

    public static String getProperty(final Map<String, String> properties, String key) {
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

    private MvnArtifact(
            final String groupId,
            final String artifactId,
            final String type,
            final String version)
            throws IOException, XmlPullParserException {

        mPrefix = String.format(
                "%1$s/%2$s/%3$s/%2$s-%3$s",
                groupId.replaceAll("\\.", "/"),
                artifactId,
                version);

        final var repository = MvnTools.getRepository();
        mPom = new File(repository, mPrefix + ".pom").getCanonicalFile();

        if (!mPom.exists()) {
            final var procBuilder = new ProcessBuilder(
                    "mvn",
                    "dependency:get",
                    "-DgroupId=" + groupId,
                    "-DartifactId=" + artifactId,
                    "-Dpackaging=" + type,
                    "-Dversion=" + version)
                    .inheritIO()
                    .directory(new File("."));
            final var proc = procBuilder.start();

            try {
                final var code = proc.waitFor();
                if (code != 0) {
                    throw new IllegalStateException(
                            String.format(
                                    "Maven failed to get '%s:%s:%s': Exit code %d",
                                    groupId,
                                    artifactId,
                                    version,
                                    code));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        final var model = MvnTools.getModel(mPom);

        if (model.getParent() != null) {
            mParent = getArtifact(model.getParent().getId());
            if (mParent != null)
                mProperties.putAll(mParent.mProperties);
        } else {
            mParent = null;
        }

        model.getProperties().forEach((key, value) -> mProperties.put((String) key, (String) value));

        mGroupId = model.getGroupId() == null ? model.getParent().getGroupId() : model.getGroupId();
        mArtifactId = model.getArtifactId();
        mPackaging = model.getPackaging();
        mVersion = model.getVersion() == null ? model.getParent().getVersion() : model.getVersion();

        mProperties.put("project.version", mVersion);

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
            dependencies.add(artifact);
        }
        mDependencies = dependencies.toArray(new MvnArtifact[0]);
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
        return mGroupId + ':' + mArtifactId + ':' + mPackaging + ':' + mVersion;
    }

    public String getPrefix() {
        return mPrefix;
    }

    public File getPom() {
        return mPom;
    }

    public MvnArtifact getParent() {
        return mParent;
    }

    public MvnArtifact[] getDependencies() {
        return mDependencies;
    }

    public void dumpTree() {
        System.out.println(getId());
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].dumpTree(1, new Vector<>(), i == mDependencies.length - 1);
    }

    private void dumpTree(int depth, List<Boolean> wasLast, boolean last) {
        String spaces = "";
        for (int i = 1; i < depth; ++i)
            spaces += wasLast.get(i - 1) ? "   " : "|  ";
        spaces += last ? "\\- " : "+- ";
        wasLast.add(last);
        System.out.printf("%s%s%n", spaces, getId());
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].dumpTree(depth + 1, wasLast, i == mDependencies.length - 1);
    }
}
