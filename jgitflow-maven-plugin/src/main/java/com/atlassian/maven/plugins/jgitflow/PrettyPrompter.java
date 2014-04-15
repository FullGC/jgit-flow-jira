package com.atlassian.maven.plugins.jgitflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

import jline.ANSIBuffer;

/**
 * @since version
 */
public class PrettyPrompter implements Prompter
{
    //maven-cli-plugin uses an old version jline that has ansi codes in package scope.
    //re-defining them in public here
    public static final int OFF = 0;
    public static final int BOLD = 1;
    public static final int UNDERSCORE = 4;
    public static final int BLINK = 5;
    public static final int REVERSE = 7;
    public static final int CONCEALED = 8;
    public static final int FG_BLACK = 30;
    public static final int FG_RED = 31;
    public static final int FG_GREEN = 32;
    public static final int FG_YELLOW = 33;
    public static final int FG_BLUE = 34;
    public static final int FG_MAGENTA = 35;
    public static final int FG_CYAN = 36;
    public static final int FG_WHITE = 37;
    public static final char ESC = 27;


    /**
     * @requirement
     */
    private OutputHandler outputHandler;

    /**
     * @requirement
     */
    private InputHandler inputHandler;

    private boolean useAnsiColor;

    public PrettyPrompter()
    {
        String mavencolor = System.getenv("MAVEN_COLOR");
        if (mavencolor != null && !mavencolor.equals(""))
        {
            useAnsiColor = Boolean.parseBoolean(mavencolor);
        } else
        {
            useAnsiColor = false;
        }
    }
    
    public void setCygwinTerminal()
    {
        ((ConsoleInputHandler) inputHandler).setCygwinTerminal();
    }

    public String promptNotBlank(String message) throws PrompterException
    {
        return promptNotBlank(message, null);
    }

    public String promptNotBlank(String message, String defaultValue) throws PrompterException
    {
        String value;
        if (StringUtils.isBlank(defaultValue))
        {
            value = prompt(requiredMessage(message));
        } else
        {
            value = prompt(message, defaultValue);
        }

        if (StringUtils.isBlank(value))
        {
            value = promptNotBlank(message, defaultValue);
        }
        return value;
    }

    public String requiredMessage(String message)
    {
        String formattedMessage = message;
        if (useAnsiColor)
        {
            ANSIBuffer ansiBuffer = new ANSIBuffer();
            ansiBuffer.append(ANSIBuffer.ANSICodes
                                        .attrib(PrettyPrompter.BOLD))
                      .append(ANSIBuffer.ANSICodes
                                        .attrib(PrettyPrompter.FG_RED))
                      .append(message)
                      .append(ANSIBuffer.ANSICodes
                                        .attrib(PrettyPrompter.OFF));
            formattedMessage = ansiBuffer.toString();
        }

        return formattedMessage;
    }
    
    public String prompt(String message)
            throws PrompterException
    {
        try
        {
            writePrompt(message);
        } catch (IOException e)
        {
            throw new PrompterException("Failed to present prompt", e);
        }

        try
        {
            return StringUtils.trim(inputHandler.readLine());
        } catch (IOException e)
        {
            throw new PrompterException("Failed to read user response", e);
        }
    }

    public String prompt(String message, String defaultReply)
            throws PrompterException
    {
        try
        {
            writePrompt(formatMessage(message, null, defaultReply));
        } catch (IOException e)
        {
            throw new PrompterException("Failed to present prompt", e);
        }

        try
        {
            String line = inputHandler.readLine();

            if (StringUtils.isEmpty(line))
            {
                line = defaultReply;
            }

            return StringUtils.trim(line);
        } catch (IOException e)
        {
            throw new PrompterException("Failed to read user response", e);
        }
    }

    public String prompt(String message, List possibleValues, String defaultReply)
            throws PrompterException
    {
        String formattedMessage = formatMessage(message, possibleValues, defaultReply);

        String line;

        do
        {
            try
            {
                writePrompt(formattedMessage);
            } catch (IOException e)
            {
                throw new PrompterException("Failed to present prompt", e);
            }

            try
            {
                line = inputHandler.readLine();
            } catch (IOException e)
            {
                throw new PrompterException("Failed to read user response", e);
            }

            if (StringUtils.isEmpty(line))
            {
                line = defaultReply;
            }

            if (line != null && !possibleValues.contains(line))
            {
                try
                {
                    String invalid = "Invalid selection.";
                    if (useAnsiColor)
                    {
                        ANSIBuffer ansiBuffer = new ANSIBuffer();
                        ansiBuffer.append(ANSIBuffer.ANSICodes
                                                    .attrib(FG_RED))
                                  .append(ANSIBuffer.ANSICodes
                                                    .attrib(BOLD))
                                  .append("Invalid selection.")
                                  .append(ANSIBuffer.ANSICodes
                                                    .attrib(OFF));
                        invalid = ansiBuffer.toString();
                    }
                    outputHandler.writeLine(invalid);
                } catch (IOException e)
                {
                    throw new PrompterException("Failed to present feedback", e);
                }
            }
        }
        while (line == null || !possibleValues.contains(line));

        return StringUtils.trim(line);
    }

    public String promptNumberedList(String message, List<String> possibleValues) throws PrompterException
    {
        return promptNumberedList(message,possibleValues,null);
    }

    public String promptNumberedList(String message, List<String> possibleValues, String defaultValue) throws PrompterException
    {
        MessageAndAnswers ma = formatNumberedMessage(message, possibleValues,defaultValue);

        String answer = prompt(ma.message, ma.answers, ma.defaultAnswer);
        
        int answerInt = Integer.parseInt(answer);
        
        return possibleValues.get((answerInt -1));
    }


    public String prompt(String message, List possibleValues)
            throws PrompterException
    {
        return prompt(message, possibleValues, null);
    }

    public String promptForPassword(String message)
            throws PrompterException
    {
        try
        {
            writePrompt(message);
        } catch (IOException e)
        {
            throw new PrompterException("Failed to present prompt", e);
        }

        try
        {
            return inputHandler.readPassword();
        } catch (IOException e)
        {
            throw new PrompterException("Failed to read user response", e);
        }
    }

    protected String formatMessage(String message, List possibleValues, String defaultReply)
    {
        if (useAnsiColor)
        {
            return formatAnsiMessage(message, possibleValues, defaultReply);
        } else
        {
            return formatPlainMessage(message, possibleValues, defaultReply);
        }
    }

    private MessageAndAnswers formatNumberedMessage(String message, List<String> possibleValues, String defaultValue)
    {
        if (useAnsiColor)
        {
            return formatNumberedAnsiMessage(message, possibleValues, defaultValue);
        } 
        else
        {
            return formatNumberedPlainMessage(message, possibleValues, defaultValue);
        }
    }

    private String formatAnsiMessage(String message, List possibleValues, String defaultReply)
    {
        ANSIBuffer formatted = new ANSIBuffer();

        formatted.append(message);

        if (possibleValues != null && !possibleValues.isEmpty())
        {
            formatted.append(" (");

            for (Iterator it = possibleValues.iterator(); it.hasNext(); )
            {
                String possibleValue = (String) it.next();

                formatted.attrib(possibleValue, BOLD);

                if (it.hasNext())
                {
                    formatted.append("/");
                }
            }

            formatted.append(")");
        }

        if (defaultReply != null)
        {
            formatted.append(ANSIBuffer.ANSICodes
                                       .attrib(FG_GREEN))
                     .append(ANSIBuffer.ANSICodes
                                       .attrib(BOLD))
                     .append(" [")
                     .append(defaultReply)
                     .append("]")
                     .append(ANSIBuffer.ANSICodes
                                       .attrib(OFF));
        }

        return formatted.toString();
    }

    private MessageAndAnswers formatNumberedAnsiMessage(String message, List<String> possibleValues, String defaultValue)
    {
        ANSIBuffer formatted = new ANSIBuffer();
        formatted.bold(message).append("\n");

        List<String> answers = new ArrayList<String>();
        String defaultAnswer = "1";
        int counter = 1;

        for (String val : possibleValues)
        {

            String answer = String.valueOf(counter);
            if(val.equals(defaultValue))
            {
                formatted.bold(answer);
                defaultAnswer = answer;
            }
            else
            {
                formatted.append(answer);
            }

            if (counter < 10)
            {
                formatted.append(":  ");
            } else
            {
                formatted.append(": ");
            }

            if(val.equals(defaultValue))
            {
                formatted.bold(val).append("\n");
            }
            else
            {
                formatted.append(val).append("\n");
            }

            answers.add(answer);

            counter++;
        }

        formatted.bold("Choose a number");

        return new MessageAndAnswers(formatted.toString(),answers,defaultAnswer);
    }

    private String formatPlainMessage(String message, List possibleValues, String defaultReply)
    {
        StringBuffer formatted = new StringBuffer(message.length() * 2);

        formatted.append(message);

        if (possibleValues != null && !possibleValues.isEmpty())
        {
            formatted.append(" (");

            for (Iterator it = possibleValues.iterator(); it.hasNext(); )
            {
                String possibleValue = (String) it.next();

                formatted.append(possibleValue);

                if (it.hasNext())
                {
                    formatted.append('/');
                }
            }

            formatted.append(')');
        }

        if (defaultReply != null)
        {
            formatted.append(" [")
                     .append(defaultReply)
                     .append("]");
        }

        return formatted.toString();
    }

    private MessageAndAnswers formatNumberedPlainMessage(String message, List<String> possibleValues, String defaultValue)
    {
        StringBuffer formatted = new StringBuffer();
        formatted.append(message).append("\n");

        List<String> answers = new ArrayList<String>();

        int counter = 1;
        String defaultAnswer = "1";
        for (String val : possibleValues)
        {

            String answer = String.valueOf(counter);
            formatted.append(answer);

            if(val.equals(defaultValue))
            {
                defaultAnswer = answer;
            }

            if (counter < 10)
            {
                formatted.append(":  ");
            } else
            {
                formatted.append(": ");
            }
            
            formatted.append(val).append("\n");

            answers.add(answer);

            counter++;
        }

        formatted.append("Choose a number");

        return new MessageAndAnswers(formatted.toString(),answers,defaultAnswer);
    }

    private void writePrompt(String message)
            throws IOException
    {
        outputHandler.write(message + ": ");
    }

    public void showMessage(String message)
            throws PrompterException
    {
        try
        {
            writePrompt(message);
        } catch (IOException e)
        {
            throw new PrompterException("Failed to present prompt", e);
        }

    }
    
    private class MessageAndAnswers
    {
        private final String message;
        private final List<String> answers;
        private final String defaultAnswer;

        private MessageAndAnswers(String message, List<String> answers, String defaultAnswer)
        {
            this.message = message;
            this.answers = answers;
            this.defaultAnswer = defaultAnswer;
        }

    }


}