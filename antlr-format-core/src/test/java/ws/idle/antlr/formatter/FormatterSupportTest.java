package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

class FormatterSupportTest {

    @Test
    void tokenStreamsBuiltFromTextAndTokensExposeEquivalentMetadata() {
        String grammar = "grammar Demo;\n// comment\na:'a';\n";

        FormatterTokenStream fromGrammar = FormatterTokenStream.fromGrammar(grammar);

        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        FormatterTokenStream fromTokens = FormatterTokenStream.fromTokens(tokenStream.getTokens());

        assertEquals(grammar, fromGrammar.sourceText());
        assertEquals(grammar, fromTokens.sourceText());
        assertEquals(fromGrammar.size(), fromTokens.size());

        int ruleIndex = grammar.indexOf("a:'a'");
        int fromGrammarTokenIndex = fromGrammar.tokenIndexForCharIndex(ruleIndex, true);
        int fromTokensTokenIndex = fromTokens.tokenIndexForCharIndex(ruleIndex, true);

        assertEquals(fromGrammar.type(fromGrammarTokenIndex), fromTokens.type(fromTokensTokenIndex));
        assertEquals(ANTLRv4Lexer.RULE_REF, fromGrammar.type(fromGrammarTokenIndex));
    }

    @Test
    void tokenStreamHandlesBoundaryLookupsAndSourceSlices() {
        String grammar = "grammar Demo;\na:'a';\n";
        FormatterTokenStream tokenStream = FormatterTokenStream.fromGrammar(grammar);

        assertEquals(0, tokenStream.tokenIndexForCharIndex(-10, true));
        assertEquals(tokenStream.size() - 1, tokenStream.tokenIndexForCharIndex(grammar.length() + 10, false));

        int ruleIndex = grammar.indexOf("a:'a'");
        assertEquals("a:'a'", tokenStream.sourceSlice(ruleIndex, ruleIndex + 4));
    }

    @Test
    void commentHelperReflowsLongLineComments() {
        FormattingOptions options = FormattingOptions.defaults();
        options.columnLimit = 24;

        String comment = "// this comment should wrap nicely";
        String reflowed = FormatterComments.reflowComment(comment, ANTLRv4Lexer.LINE_COMMENT, options, 0, 0);

        assertTrue(reflowed.contains("\n// "));
    }

    @Test
    void commentHelperReflowsMultilineBlockCommentsWithIndentation() {
        FormattingOptions options = FormattingOptions.defaults();
        options.columnLimit = 18;
        options.indentWidth = 2;

        String comment = "/*\n * alpha beta gamma delta\n */";
        String reflowed = FormatterComments.reflowComment(comment, ANTLRv4Lexer.BLOCK_COMMENT, options, 0, 1);

        assertTrue(reflowed.startsWith("/*"));
        assertTrue(reflowed.contains("\n   * "));
        assertTrue(reflowed.endsWith(" */"));
    }

    @Test
    void outputNormalizationUsesRequestedLineSeparatorAndAppendsTrailingNewline() {
        String normalized = FormattingOutputs.normalizeForOutput("grammar Demo;\ra: 'a';", "\r\n");

        assertEquals("grammar Demo;\r\na: 'a';\r\n", normalized);
    }

    @Test
    void directiveParserUnderstandsFormatterComments() {
        String comment = "// $antlr-format alignLabels on, columnLimit 120, alignColons trailing";

        FormatterDirectiveParser.ParseResult result = FormatterDirectiveParser.parse(comment);

        assertTrue(result.containsFormattingOptions());
        assertEquals(3, result.directives().size());
        assertInstanceOf(FormatterDirectiveParser.BooleanOptionDirective.class, result.directives().get(0));
        assertInstanceOf(FormatterDirectiveParser.IntOptionDirective.class, result.directives().get(1));
        assertInstanceOf(FormatterDirectiveParser.ColonAlignmentDirective.class, result.directives().get(2));
    }

    @Test
    void directiveParserAcceptsBareFormatterComment() {
        FormatterDirectiveParser.ParseResult result = FormatterDirectiveParser.parse("// $antlr-format");

        assertTrue(result.containsFormattingOptions());
        assertTrue(result.directives().isEmpty());
    }

    @Test
    void directiveParserAcceptsColonAfterFormatterIntroducer() {
        FormatterDirectiveParser.ParseResult result = FormatterDirectiveParser.parse(
            "// $antlr-format: columnLimit: 120, alignLabels on");

        assertTrue(result.containsFormattingOptions());
        assertEquals(2, result.directives().size());
        assertInstanceOf(FormatterDirectiveParser.IntOptionDirective.class, result.directives().get(0));
        assertInstanceOf(FormatterDirectiveParser.BooleanOptionDirective.class, result.directives().get(1));
    }

    @Test
    void directiveParserUnderstandsMultilineBlockComments() {
        String comment = """
            /*
             * $antlr-format alignLabels on,
             * columnLimit 120,
             * alignColons trailing
             */
            """;

        FormatterDirectiveParser.ParseResult result = FormatterDirectiveParser.parse(comment);

        assertTrue(result.containsFormattingOptions());
        assertEquals(3, result.directives().size());
        assertInstanceOf(FormatterDirectiveParser.BooleanOptionDirective.class, result.directives().get(0));
        assertInstanceOf(FormatterDirectiveParser.IntOptionDirective.class, result.directives().get(1));
        assertInstanceOf(FormatterDirectiveParser.ColonAlignmentDirective.class, result.directives().get(2));
    }

    @Test
    void directiveParserIgnoresNearbyPrefixesAndPreservesInvalidEntries() {
        FormatterDirectiveParser.ParseResult none = FormatterDirectiveParser.parse("// $antlr-formatting off");
        FormatterDirectiveParser.ParseResult result = FormatterDirectiveParser.parse(
            "// $antlr-format alignSemicolons ownLine, reset, useTab off, mystery maybe");

        assertFalse(none.containsFormattingOptions());
        assertTrue(result.containsFormattingOptions());
        assertEquals(4, result.directives().size());
        assertInstanceOf(FormatterDirectiveParser.SemicolonAlignmentDirective.class, result.directives().get(0));
        assertInstanceOf(FormatterDirectiveParser.ResetDirective.class, result.directives().get(1));
        assertInstanceOf(FormatterDirectiveParser.BooleanOptionDirective.class, result.directives().get(2));
        assertInstanceOf(FormatterDirectiveParser.InvalidDirective.class, result.directives().get(3));
    }
}

