## Release Notes - Maven JGit Flow

### Version 1.0-m5 
(14/May/2015)
#### Bug
* [MJF-118](https://ecosystem.atlassian.net/browse/MJF-118) - start-release fails with ```org.eclipse.jgit.errors.TransportException: ssh://...: Auth fail```
* [MJF-134](https://ecosystem.atlassian.net/browse/MJF-134) - Error if ```<artifactId>``` not at top of POM
* [MJF-178](https://ecosystem.atlassian.net/browse/MJF-178) - feature-finish uses release profile
* [MJF-179](https://ecosystem.atlassian.net/browse/MJF-179) - Snapshot dependencies hard to find when detected on release-start
* [MJF-187](https://ecosystem.atlassian.net/browse/MJF-187) - NullPointerException on release-start
* [MJF-191](https://ecosystem.atlassian.net/browse/MJF-191) - release-start proclaiming success despite underlying error
* [MJF-193](https://ecosystem.atlassian.net/browse/MJF-193) - featureRebase=True leads to "Parameter "upstream" is missing"
* [MJF-194](https://ecosystem.atlassian.net/browse/MJF-194) - Error starting feature: Error verifying initial version state in poms: Cannot start a feature due to snapshot dependencies
* [MJF-197](https://ecosystem.atlassian.net/browse/MJF-197) - Build fails if scmCommentPrefix has space in it
* [MJF-198](https://ecosystem.atlassian.net/browse/MJF-198) - scmCommentPrefix value is not used for merge messages for release-finish goal
* [MJF-204](https://ecosystem.atlassian.net/browse/MJF-204) - Unable to merge on release/hotfix finish if develop has a new module
* [MJF-206](https://ecosystem.atlassian.net/browse/MJF-206) - UpdatePomsWithNonSnapshotCommand hides exception information
* [MJF-207](https://ecosystem.atlassian.net/browse/MJF-207) - hotfix-finish fails if keepBranch=false and release branch present
* [MJF-208](https://ecosystem.atlassian.net/browse/MJF-208) - release-finish fails after hotfix-finish
* [MJF-210](https://ecosystem.atlassian.net/browse/MJF-210) - UpdatePomsWithSnapshotsCommand commit message is misleading
* [MJF-227](https://ecosystem.atlassian.net/browse/MJF-227) - Generate Documentation using Maven Reflow
#### Improvement
* [MJF-81](https://ecosystem.atlassian.net/browse/MJF-81) - Use commit prefix for merge commit message
* [MJF-165](https://ecosystem.atlassian.net/browse/MJF-165) - hotfix finish to back merge into release branch
* [MJF-176](https://ecosystem.atlassian.net/browse/MJF-176) - Calculate next development version based on overridden release version if specified
* [MJF-183](https://ecosystem.atlassian.net/browse/MJF-183) - Choose EOL for pom updates
* [MJF-214](https://ecosystem.atlassian.net/browse/MJF-214) - Add a arguments property on goal release-finish to pass additional arguments to pass to the Maven executions.
* [MJF-215](https://ecosystem.atlassian.net/browse/MJF-215) - Add a goals property on release-finish mojo for goals to execute