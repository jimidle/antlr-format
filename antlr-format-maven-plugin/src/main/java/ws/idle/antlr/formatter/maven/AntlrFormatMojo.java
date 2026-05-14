package ws.idle.antlr.formatter.maven;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import ws.idle.antlr.formatter.AntlrFormatterService;
import ws.idle.antlr.formatter.FormattingConfiguration;
import ws.idle.antlr.formatter.FormattingOptions;
import ws.idle.antlr.formatter.FormattingResult;

/** Maven goal that formats ANTLR grammar files in-place. */
@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class AntlrFormatMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}/src/main/antlr4")
    private Path sourceDirectory;

    @Parameter
    private List<String> includes;

    @Parameter
    private List<String> excludes;

    @Parameter(defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "true")
    private boolean addOptions;

    @Parameter(defaultValue = "false")
    private boolean dryRun;

    @Parameter(defaultValue = "UTF-8")
    private String encoding;

    @Parameter
    private FormattingOptions main;

    @Parameter
    private FormattingOptions lexer;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping ANTLR formatting.");
            return;
        }

        if (sourceDirectory == null || !Files.isDirectory(sourceDirectory)) {
            getLog().info("ANTLR source directory not found: " + sourceDirectory);
            return;
        }

        List<String> includeGlobs = (includes == null || includes.isEmpty())
            ? List.of("**/*.g4")
            : includes;
        List<String> excludeGlobs = excludes == null ? List.of() : excludes;

        List<Path> files = collectFiles(sourceDirectory, includeGlobs, excludeGlobs);
        if (files.isEmpty()) {
            getLog().info("No grammar files matched include/exclude rules.");
            return;
        }

        Charset charset = resolveCharset(encoding);

        FormattingConfiguration configuration = new FormattingConfiguration();
        configuration.main = main == null ? new FormattingOptions() : main;
        configuration.lexer = lexer;

        AntlrFormatterService formatterService = new AntlrFormatterService();

        int changed = 0;
        for (Path file : files) {
            String input;
            try {
                input = Files.readString(file, charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read grammar: " + file, e);
            }

            FormattingResult result = formatterService.format(input, configuration, addOptions, 0, Integer.MAX_VALUE);
            if (!input.equals(result.text())) {
                changed++;
                if (dryRun) {
                    getLog().info("Would format: " + file);
                } else {
                    try {
                        Files.writeString(file, result.text(), charset);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Failed to write formatted grammar: " + file, e);
                    }
                    getLog().info("Formatted: " + file);
                }
            }
        }

        if (dryRun) {
            getLog().info("Dry run complete. Files that would change: " + changed);
        } else {
            getLog().info("Formatting complete. Files changed: " + changed);
        }
    }

    private static List<Path> collectFiles(Path root, List<String> includeGlobs, List<String> excludeGlobs)
        throws MojoExecutionException {

        var includeMatchers = includeGlobs.stream()
            .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
            .toList();
        var excludeMatchers = excludeGlobs.stream()
            .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
            .toList();

        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .forEach(path -> {
                    Path relative = root.relativize(path);
                    boolean include = includeMatchers.stream().anyMatch(matcher -> matcher.matches(relative));
                    if (!include) {
                        return;
                    }

                    boolean exclude = excludeMatchers.stream().anyMatch(matcher -> matcher.matches(relative));
                    if (!exclude) {
                        result.add(path);
                    }
                });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan grammar files in " + root, e);
        }

        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private static Charset resolveCharset(String encoding) throws MojoExecutionException {
        String charsetName = encoding == null ? StandardCharsets.UTF_8.name() : encoding;
        try {
            return Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unsupported encoding configured for antlr-format: " + charsetName, e);
        }
    }
}

