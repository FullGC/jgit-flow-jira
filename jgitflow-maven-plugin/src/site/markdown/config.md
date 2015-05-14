## Common Parameters
All of the goals some common configuration parameters defined in ```com.atlassian.maven.plugins.jgitflow.AbstractJGitFlowMojo```. 

One complex parameter is **flowInitContext**. This parameter permits you to configure branch and tag names, as shows in the following example:

```xml
    <configuration>
      <flowInitContext>
         <masterBranchName>frankenstein</masterBranchName>
         <developBranchName>development</developBranchName>
         <featureBranchPrefix>feature-</featureBranchPrefix>
         <releaseBranchPrefix>release-</releaseBranchPrefix>
         <hotfixBranchPrefix>hotfix-</hotfixBranchPrefix>
         <versionTagPrefix>blither-</versionTagPrefix>
       </flowInitContext>
     </configuration>
```

With this configuration, the 'master' branch is named 'frankenstein' and the 'develop' branch is named 'development'. 

Feature branches are named 'feature-Whatever' instead of feature/whatever, release branches 'release-Version' instead of 'release/Version', and similarly for hotfixes. 

Version tags are named 'blither-Version' instead of 'Version'.

## Username/Password configuration

For setting an explicit username/password configuration you can use the following snippet:

```xml
    <configuration>
      <username>MY_USER</username>
      <password>MY_PW</password>
    </configuration>
```

Full documentation about all of the possible parameter configurations can be found on the [Plugin Documentation page](plugin-info.html)