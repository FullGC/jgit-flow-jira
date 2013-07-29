package ut.com.atlassian.maven.plugins.jgitflow;

import com.atlassian.maven.plugins.jgitflow.util.NamingUtil;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @since version
 */
public class NamingUtilTest
{
    @Test
    public void lowerCamel() throws Exception
    {
        String expected = "some-feature";
        String test = "someFeature";
        
        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }

    @Test
    public void upperCamel() throws Exception
    {
        String expected = "some-feature";
        String test = "SomeFeature";

        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }

    @Test
    public void upperSpaced() throws Exception
    {
        String expected = "some-feature";
        String test = "Some Feature";

        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }

    @Test
    public void lowerSpaced() throws Exception
    {
        String expected = "some-feature";
        String test = "some Feature";

        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }

    @Test
    public void acronymCamel() throws Exception
    {
        String expected = "some-feature";
        String test = "SOMEFeature";

        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }

    @Test
    public void issueCamel() throws Exception
    {
        String expected = "acdev-1286-some-feature";
        String test = "ACDEV-1286-some-feature";

        assertEquals(expected, NamingUtil.camelCaseOrSpaceToDashed(test));

    }
}
