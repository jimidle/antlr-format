grammar BreakBeforeBraces;

options {
    superClass = BaseParser;
}

tokens {
    TOKEN_A,
    TOKEN_B
}

channels {
    COMMENTS_CHANNEL
}

@parser::members {int value() { return 1; }}

defaultRule: 'a' {doIt();};

// $antlr-format breakBeforeBraces on
options
{
    superClass = BaseParser;
}

tokens
{
    TOKEN_A,
    TOKEN_B
}

channels
{
    COMMENTS_CHANNEL
}

@parser::members
{int value() { return 1; }}

breakBeforeRule: 'b' {doIt();};