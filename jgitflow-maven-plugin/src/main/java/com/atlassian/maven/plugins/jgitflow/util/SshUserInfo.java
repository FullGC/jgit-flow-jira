package com.atlassian.maven.plugins.jgitflow.util;

import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;

import com.google.common.base.Strings;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import org.codehaus.plexus.components.interactivity.PrompterException;

public class SshUserInfo implements UserInfo, UIKeyboardInteractive
{
    private PrettyPrompter prompter;
    private String password;
    private String passphrase;

    public SshUserInfo(PrettyPrompter prompter)
    {
        this.prompter = prompter;
    }

    public String getPassword()
    {
        return password;
    }

    public boolean promptYesNo(String str)
    {
        return true;
    }

    public String getPassphrase()
    {
        return passphrase;
    }

    public boolean promptPassphrase(String message)
    {
        try
        {
            passphrase = prompter.promptForPassword("Please enter your passphrase");
        }
        catch (PrompterException e)
        {
            //ignore
        }

        return (!Strings.isNullOrEmpty(passphrase));
    }

    public boolean promptPassword(String message)
    {
        try
        {
            password = prompter.promptForPassword("Please enter your password");
        }
        catch (PrompterException e)
        {
            //ignore
        }

        return (!Strings.isNullOrEmpty(password));
    }

    public void showMessage(String message)
    {
        try
        {
            prompter.showMessage(message);
        }
        catch (PrompterException e)
        {
            System.out.println(message);
        }
    }

    public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo)
    {
        String[] response = new String[prompt.length];

        return response;
    }
}
