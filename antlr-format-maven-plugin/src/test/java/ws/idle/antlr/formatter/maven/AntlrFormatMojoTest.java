package ws.idle.antlr.formatter.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ws.idle.antlr.formatter.AntlrFormatterService;
import ws.idle.antlr.formatter.FormattingConfiguration;
import ws.idle.antlr.formatter.FormattingOptions;

class AntlrFormatMojoTest {

    private static final String RELATIVE_GRAMMAR_PATH = "nested/Demo.g4";
    private static final String UNFORMATTED_GRAMMAR = "grammar Demo;\na:'a';\n";

    @TempDir
    Path tempDir;

    @Test
    void formatsMatchingGrammarFilesInPlace() throws Exception {
        Path grammar = writeGrammar();
        AntlrFormatMojo mojo = configuredMojo(tempDir);

        mojo.execute();

        String actual = Files.readString(grammar, StandardCharsets.UTF_8);
        assertEquals(expectedFormatting(), actual);
    }

    @Test
    void dryRunLeavesSourceUnchanged() throws Exception {
        Path grammar = writeGrammar();
        AntlrFormatMojo mojo = configuredMojo(tempDir);
        setField(mojo, "dryRun", true);

        mojo.execute();

        assertEquals(UNFORMATTED_GRAMMAR, Files.readString(grammar, StandardCharsets.UTF_8));
    }

    @Test
    void excludesPreventFormatting() throws Exception {
        Path grammar = writeGrammar();
        AntlrFormatMojo mojo = configuredMojo(tempDir);
        setField(mojo, "excludes", List.of("nested/**"));

        mojo.execute();

        assertEquals(UNFORMATTED_GRAMMAR, Files.readString(grammar, StandardCharsets.UTF_8));
    }

    @Test
    void invalidEncodingFailsClearly() throws Exception {
        writeGrammar();
        AntlrFormatMojo mojo = configuredMojo(tempDir);
        setField(mojo, "encoding", "definitely-not-a-charset");

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(exception.getMessage().contains("Unsupported encoding configured for antlr-format"));
    }

    private AntlrFormatMojo configuredMojo(Path sourceDirectory) throws Exception {
        AntlrFormatMojo mojo = new AntlrFormatMojo();
        setField(mojo, "sourceDirectory", sourceDirectory);
        setField(mojo, "addOptions", false);
        setField(mojo, "main", new FormattingOptions());
        return mojo;
    }

    private Path writeGrammar() throws IOException {
        Path file = tempDir.resolve(RELATIVE_GRAMMAR_PATH);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, UNFORMATTED_GRAMMAR, StandardCharsets.UTF_8);
    }

    private String expectedFormatting() {
        FormattingConfiguration configuration = new FormattingConfiguration();
        configuration.main = new FormattingOptions();
        return new AntlrFormatterService().format(UNFORMATTED_GRAMMAR, configuration, false, 0, Integer.MAX_VALUE).text();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

