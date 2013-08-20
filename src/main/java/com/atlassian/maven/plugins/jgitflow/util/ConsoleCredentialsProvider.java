package com.atlassian.maven.plugins.jgitflow.util;

import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.google.common.base.Strings;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Ask username and password to the user and remember them for the whole session.
 */
public class ConsoleCredentialsProvider extends CredentialsProvider
{
    private PrettyPrompter prompter;

    private String userName;
    private String password;

    public ConsoleCredentialsProvider(PrettyPrompter prompter) {
        this.prompter = prompter;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem i : items) {
            if (i instanceof CredentialItem.StringType)
                continue;

            else if (i instanceof CredentialItem.CharArrayType)
                continue;

            else if (i instanceof CredentialItem.YesNoType)
                continue;

            else if (i instanceof CredentialItem.InformationalMessage)
                continue;

            else
                return false;
        }
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem
    {
        boolean ok = true;
        for (CredentialItem item : items)
        {
            if (item instanceof CredentialItem.StringType)
            {
                ok = get((CredentialItem.StringType) item);
            }
            else if (item instanceof CredentialItem.CharArrayType)
            {
                ok = get((CredentialItem.CharArrayType) item);
            }
            else if (item instanceof CredentialItem.InformationalMessage)
            {
                ok = get((CredentialItem.InformationalMessage) item);
            }
            else
            {
                throw new UnsupportedCredentialItem(uri, item.getPromptText());
            }
        }

        return ok;
    }

    private boolean get(CredentialItem.StringType item) {
        if (item.isValueSecure())
        {
            String v = askPassword(item.getPromptText());
            if (v != null)
            {
                item.setValue(v);
                return true;
            }
            else
            {
                return false;
            }
        } else {
            String v = askUser(item.getPromptText());
            if (v != null)
            {
                item.setValue(v);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private boolean get(CredentialItem.CharArrayType item) {
        if (item.isValueSecure())
        {
            String v = askPassword(item.getPromptText());
            if (v != null)
            {
                item.setValue(v.toCharArray());
                return true;
            }
            else
            {
                return false;
            }
        } else {
            String v = askUser(item.getPromptText());
            if (v != null)
            {
                item.setValue(v.toCharArray());
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private boolean get(CredentialItem.InformationalMessage item) {
        try {
            prompter.promptForPassword(item.getPromptText());
        } catch (PrompterException e) {
            e.printStackTrace();
        }
        return true;
    }

    private String askPassword(String prompted) {
        try {
            if (this.password == null) {
                this.password = Strings.emptyToNull(prompter.promptForPassword(prompted));
            }
            return this.password;
        } catch (PrompterException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String askUser(String prompted) {
        try {
            if (this.userName == null) {
                this.userName = Strings.emptyToNull(prompter.prompt(prompted));
            }
            return this.userName;
        } catch (PrompterException e) {
            e.printStackTrace();
        }

        return null;
    }
}
