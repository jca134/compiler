lexer grammar LangLexer;

@header { package compiler.grammar; }

BASIC  : 'int' | 'float' | 'boolean';
WHILE  : 'while';
TRUE   : 'true';
FALSE  : 'false';
ID     : [a-zA-Z_] [a-zA-Z_0-9]*;
NUM    : [0-9]+ ;
REAL
    : [0-9]+ '.' [0-9]* ( [eE] [+-]? [0-9]+ )?      // e.g. 123.456, 0.5, 10.e2
    | '.' [0-9]+  ( [eE] [+-]? [0-9]+ )?            // e.g. .5, .5e+3
    | [0-9]+ [eE] [+-]? [0-9]+                     // e.g. 1e10, 2E-3
    ;
EQUALS : '=';
PLUS   : '+';
MINUS  : '-';
STAR   : '*';
SLASH  : '/';
SEMI   : ';';
LPAREN : '(';
RPAREN : ')';
LBRACKET : '[';
RBRACKET : ']';
LBRACE : '{';
RBRACE : '}';
WS     : [ \t\r\n]+ -> skip ;
BLOCK_COMMENT : '/*' .*? '*/'  -> skip ;
LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
