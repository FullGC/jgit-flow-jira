package com.atlassian.jgitflow.core.exception;

/**
 * Created by dani on 28/12/17.
 */
public class MissingTimeSpentException extends JiraGitFlowException {

    public MissingTimeSpentException(){
        super("Time Spent required");
    }
}
