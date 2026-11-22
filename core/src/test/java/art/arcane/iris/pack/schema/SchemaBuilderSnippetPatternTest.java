package art.arcane.iris.pack.schema;

import org.junit.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchemaBuilderSnippetPatternTest {
    @Test
    public void legacyOnlyPatternKeepsItsPlainForm() {
        assertEquals("^snippet/rate/", SchemaBuilder.snippetPattern("rate", List.of()));
    }

    @Test
    public void dotInAGroupFolderMatchesOnlyADot() {
        String pattern = SchemaBuilder.snippetPattern("rate", List.of("snippet/iris/my.group/rate"));

        assertEquals("^(?:snippet/rate|snippet/iris/my\\.group/rate)/", pattern);
        assertTrue(Pattern.compile(pattern).matcher("snippet/iris/my.group/rate/fast").find());
        assertFalse(Pattern.compile(pattern).matcher("snippet/iris/myXgroup/rate/fast").find());
    }

    @Test
    public void everyRegexSyntaxCharacterIsEscapedOnItsOwn() {
        String folder = "snippet/iris/a^$\\.*+?()[]{}|b/rate";

        String pattern = SchemaBuilder.snippetPattern("rate", List.of(folder));

        assertEquals("^(?:snippet/rate|snippet/iris/a\\^\\$\\\\\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\|b/rate)/", pattern);
        assertTrue(Pattern.compile(pattern).matcher(folder + "/fast").find());
    }
}
