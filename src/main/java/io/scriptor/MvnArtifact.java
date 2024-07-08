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

    public static MvnArtifact getArtifact(Map<String, String> properties, String id)
            throws IOException, XmlPullParserException {
        final var params = id.split(":");
        return getArtifact(properties, params[0], params[1], params.length == 3 ? params[2] : params[3]);
    }

    public static MvnArtifact getArtifact(Map<String, String> properties, String groupId, String artifactId,
            String versionId)
            throws IOException, XmlPullParserException {
        final var id = groupId + ':' + artifactId + ':' + versionId;
        if (ARTIFACTS.containsKey(id))
            return ARTIFACTS.get(id);

        final var artifact = new MvnArtifact(properties, groupId, artifactId, versionId);
        ARTIFACTS.put(id, artifact);
        return artifact;
    }

    public static String getProperty(Map<String, String> properties, String key) {
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

    private MvnArtifact(Map<String, String> properties, String groupId, String artifactId, String version)
            throws IOException, XmlPullParserException {
        mPrefix = String.format(
                "%1$s/%2$s/%3$s/%2$s-%3$s",
                groupId.replaceAll("\\.", "/"),
                artifactId,
                version);

        final var repository = MvnTools.getRepository();
        mPom = new File(repository, mPrefix + ".pom").getCanonicalFile();

        final var model = MvnTools.getModel(mPom);

        if (model.getParent() != null) {
            mParent = getArtifact(properties, model.getParent().getId());
        } else {
            mParent = null;
        }

        model.getProperties().forEach((key, value) -> properties.put((String) key, (String) value));

        mGroupId = model.getGroupId() == null ? model.getParent().getGroupId() : model.getGroupId();
        mArtifactId = model.getArtifactId();
        mPackaging = model.getPackaging();
        mVersion = model.getVersion() == null ? model.getParent().getVersion() : model.getVersion();

        if (model.getDependencyManagement() != null) {
            for (final var dependency : model.getDependencyManagement().getDependencies()) {
                final var depGroupId = getProperty(properties, dependency.getGroupId());
                final var depArtifactId = getProperty(properties, dependency.getArtifactId());
                final var depType = getProperty(properties, dependency.getType());
                final var depVersion = getProperty(properties, dependency.getVersion());
                final var depScope = getProperty(properties, dependency.getScope());

                final var id = depGroupId + '$' + depArtifactId;
                properties.put(id + ".type", depType);
                properties.put(id + ".version", depVersion);
                properties.put(id + ".scope", depScope);
            }
        }

        final List<MvnArtifact> dependencies = new Vector<>();
        for (final var dependency : model.getDependencies()) {
            final var depGroupId = getProperty(properties, dependency.getGroupId());
            final var depArtifactId = getProperty(properties, dependency.getArtifactId());
            final var id = depGroupId + '$' + depArtifactId;
            var depType = getProperty(properties, dependency.getType());
            var depVersion = getProperty(properties, dependency.getVersion());
            var depScope = getProperty(properties, dependency.getScope());

            if (depType == null)
                depType = properties.get(id + ".type");
            if (depVersion == null)
                depVersion = properties.get(id + ".version");
            if (depScope == null)
                depScope = properties.get(id + ".scope");

            if ("test".equals(depScope))
                continue;

            final var artifact = getArtifact(
                    new HashMap<>(),
                    depGroupId,
                    depArtifactId,
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
            mDependencies[i].dumpTree(1, i == mDependencies.length - 1);
    }

    private void dumpTree(int depth, boolean last) {
        String spaces = "";
        for (int i = 1; i < depth; ++i)
            spaces += "   ";
        spaces += last ? "\\- " : "+- ";
        System.out.printf("%s%s%n", spaces, getId());
        for (int i = 0; i < mDependencies.length; ++i)
            mDependencies[i].dumpTree(depth + 1, i == mDependencies.length - 1);
    }
}
