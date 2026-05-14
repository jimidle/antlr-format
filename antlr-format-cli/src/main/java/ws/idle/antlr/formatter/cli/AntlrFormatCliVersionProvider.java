package ws.idle.antlr.formatter.cli;

import picocli.CommandLine;

/**
 * Supplies the version string reported by the command line interface.
 */
public final class AntlrFormatCliVersionProvider implements CommandLine.IVersionProvider {

    /**
     * Returns the CLI version text.
     *
     * @return the version lines emitted for {@code --version}
     */
    @Override
    public String[] getVersion() {
        Package cliPackage = AntlrFormatCli.class.getPackage();
        String implementationVersion = cliPackage == null ? null : cliPackage.getImplementationVersion();
        if (implementationVersion == null || implementationVersion.isBlank()) {
            implementationVersion = "development";
        }
        return new String[] { "antlr-format-cli " + implementationVersion };
    }
}

