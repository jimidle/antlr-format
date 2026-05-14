package ws.idle.antlr.formatter.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliCompletionScriptsTest {

	@Test
	void bashCompletionIncludesNegatedFlagsAndEnumValues() {
		String script = CliCompletionScripts.bashCompletionScript();

		assertTrue(script.contains("--no-add-options"));
		assertTrue(script.contains("--no-use-tab"));
		assertTrue(script.contains("none trailing hanging"));
		assertTrue(script.contains("!*.g4"));
	}

	@Test
	void zshCompletionIncludesExpectedOptionGroups() {
		String script = CliCompletionScripts.zshCompletionScript();

		assertTrue(script.contains("#compdef antlr-format"));
		assertTrue(script.contains("{-o,--output}[Write the formatted grammar to the specified file.]:file:_files"));
		assertTrue(script.contains("--align-colons[Set the colon alignment mode.]:value:(none trailing hanging)"));
	}

	@Test
	void fishCompletionIncludesHelpVersionAndGrammarSuffixCompletion() {
		String script = CliCompletionScripts.fishCompletionScript();

		assertTrue(script.contains("-s h -l help"));
		assertTrue(script.contains("-s V -l version"));
		assertTrue(script.contains("(__fish_complete_suffix .g4)"));
	}
}

