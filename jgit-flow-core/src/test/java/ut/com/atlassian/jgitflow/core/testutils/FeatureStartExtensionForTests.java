package ut.com.atlassian.jgitflow.core.testutils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.jgitflow.core.extension.FeatureStartExtension;

import com.google.common.collect.Lists;

public class FeatureStartExtensionForTests implements FeatureStartExtension
{
    private final Map<String,WasCalledExtension> methodMap;

    public FeatureStartExtensionForTests()
    {
        this.methodMap = new HashMap<String, WasCalledExtension>();
    }

    public FeatureStartExtensionForTests withException(String methodName, ExtensionFailStrategy failStrategy)
    {
        createExtensionWithException(methodName, failStrategy);
        return this;
    }

    @Override
    public List<ExtensionCommand> beforeFetch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("beforeFetch"));
    }

    @Override
    public List<ExtensionCommand> afterFetch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("afterFetch"));
    }

    @Override
    public List<ExtensionCommand> beforeCreateBranch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("beforeCreateBranch"));
    }

    @Override
    public List<ExtensionCommand> afterCreateBranch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("afterCreateBranch"));
    }

    @Override
    public List<ExtensionCommand> afterPush()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("afterPush"));
    }

    @Override
    public List<ExtensionCommand> before()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("before"));
    }

    @Override
    public List<ExtensionCommand> after()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension("after"));
    }
    
    public boolean wasCalled(String methodName)
    {
        if(methodMap.containsKey(methodName))
        {
            return methodMap.get(methodName).wasCalled();
        }
        
        return false;
    }

    private void createExtensionWithException(String methodName, ExtensionFailStrategy failStrategy)
    {
        WasCalledExtension extension = new WasCalledExtension(true);
        extension.setFailStrategy(failStrategy);
        
        methodMap.put(methodName,extension);
    }
    
    private ExtensionCommand createExtension(String methodName)
    {
        if(methodMap.containsKey(methodName))
        {
            return methodMap.get(methodName);
        }
        
        WasCalledExtension extension = new WasCalledExtension();
        methodMap.put(methodName,extension);
        
        return extension;
    }
}
