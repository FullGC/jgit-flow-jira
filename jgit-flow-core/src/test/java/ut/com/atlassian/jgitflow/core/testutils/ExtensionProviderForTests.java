package ut.com.atlassian.jgitflow.core.testutils;

import com.atlassian.jgitflow.core.extension.*;
import com.atlassian.jgitflow.core.extension.impl.*;

public class ExtensionProviderForTests implements ExtensionProvider
{
    private ReleaseStartExtension releaseStartExtension;
    private ReleaseFinishExtension releaseFinishExtension;
    private HotfixStartExtension hotfixStartExtension;
    private HotfixFinishExtension hotfixFinishExtension;
    private FeatureStartExtension featureStartExtension;
    private FeatureFinishExtension featureFinishExtension;

    public ExtensionProviderForTests()
    {
        this.releaseStartExtension = new EmptyReleaseStartExtension();
        this.releaseFinishExtension = new EmptyReleaseFinishExtension();
        this.hotfixStartExtension = new EmptyHotfixStartExtension();
        this.hotfixFinishExtension = new EmptyHotfixFinishExtension();
        this.featureStartExtension = new EmptyFeatureStartExtension();
        this.featureFinishExtension = new EmptyFeatureFinishExtension();
    }

    @Override
    public ReleaseStartExtension provideReleaseStartExtension()
    {
        return releaseStartExtension;
    }

    @Override
    public ReleaseFinishExtension provideReleaseFinishExtension()
    {
        return releaseFinishExtension;
    }

    @Override
    public HotfixStartExtension provideHotfixStartExtension()
    {
        return hotfixStartExtension;
    }

    @Override
    public HotfixFinishExtension provideHotfixFinishExtension()
    {
        return hotfixFinishExtension;
    }

    @Override
    public FeatureStartExtension provideFeatureStartExtension()
    {
        return featureStartExtension;
    }

    @Override
    public FeatureFinishExtension provideFeatureFinishExtension()
    {
        return featureFinishExtension;
    }

    public void setReleaseStartExtension(ReleaseStartExtension releaseStartExtension)
    {
        this.releaseStartExtension = releaseStartExtension;
    }

    public void setReleaseFinishExtension(ReleaseFinishExtension releaseFinishExtension)
    {
        this.releaseFinishExtension = releaseFinishExtension;
    }

    public void setHotfixStartExtension(HotfixStartExtension hotfixStartExtension)
    {
        this.hotfixStartExtension = hotfixStartExtension;
    }

    public void setHotfixFinishExtension(HotfixFinishExtension hotfixFinishExtension)
    {
        this.hotfixFinishExtension = hotfixFinishExtension;
    }

    public void setFeatureStartExtension(FeatureStartExtension featureStartExtension)
    {
        this.featureStartExtension = featureStartExtension;
    }

    public void setFeatureFinishExtension(FeatureFinishExtension featureFinishExtension)
    {
        this.featureFinishExtension = featureFinishExtension;
    }
}
