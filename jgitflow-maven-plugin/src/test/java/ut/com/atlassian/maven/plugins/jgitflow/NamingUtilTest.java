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

    @Test
    public void oneEOLNix() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\nbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void twoEOLNix() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\n\nbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void scatteredEOLNix() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\naaaa\nbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void oneEOLWin() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\n\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void twoEOLWin() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\n\r\n\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void scatteredEOLWin() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\n\raaaa\n\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void oneEOLMac() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void twoEOLMac() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\r\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }

    @Test
    public void scatteredEOLMac() throws Exception
    {
        String expected = "bbb";
        String test = "aaa\raaaa\rbbb";

        assertEquals(expected, NamingUtil.afterLastNewline(test));

    }
}
