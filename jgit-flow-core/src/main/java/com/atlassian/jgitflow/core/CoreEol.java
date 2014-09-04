package com.atlassian.jgitflow.core;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.ConfigEnum;
import org.eclipse.jgit.lib.ConfigConstants;

public enum CoreEol implements ConfigEnum
{
    LF("lf", "\n"),
    CRLF("crlf", "\r\n"),
    NATIVE("native", System.getProperty("line.separator"));

    public static CoreEol DEFAULT = NATIVE;
    public static final String CONFIG_KEY_EOL = "eol";

    private final String configValue;
    private final String eol;

    private CoreEol(final String configValue, final String eol)
    {
        this.configValue = configValue;
        this.eol = eol;
    }

    public String getEol()
    {
        return eol;
    }

    public static CoreEol getConfigValue(final Config gitConfig)
    {
        return
                gitConfig.getEnum(CoreEol.values(),
                        ConfigConstants.CONFIG_CORE_SECTION,
                        null,
                        CONFIG_KEY_EOL,
                        DEFAULT);
    }

    @Override
    public String toConfigValue()
    {
        return configValue;
    }

    @Override
    public boolean matchConfigValue(final String in)
    {
        return configValue.equalsIgnoreCase(in);
    }
}
