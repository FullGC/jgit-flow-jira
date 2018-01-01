package com.atlassian.jgitflow.core.exception;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import org.eclipse.jgit.api.Git;

/**
 * Created by dani on 28/12/17.
 */
public class JiraGitFlowException extends JGitFlowException {
    public JiraGitFlowException(String message){super.getMessage();}
}
