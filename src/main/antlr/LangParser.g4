parser grammar LangParser;

@header { package compiler.grammar; }

options { tokenVocab=LangLexer; }

program : block EOF ;

block   : LBRACE (decl | stmt)* RBRACE ;

decl    : BASIC ID dims? SEMI ;

dims    : LBRACKET NUM RBRACKET (LBRACKET NUM RBRACKET)* ;

stmt    : assign SEMI
        | whileStmt
        | block
        ;

assign  : loc EQUALS expr ;

loc     : ID (LBRACKET expr RBRACKET)* ;

whileStmt : WHILE LPAREN expr RPAREN stmt ;

expr    : addExpr ;
addExpr : mulExpr ( (PLUS | MINUS) mulExpr )* ;
mulExpr : atom ( (STAR | SLASH) atom )* ;
atom    : REAL
        | NUM
        | TRUE
        | FALSE
        | loc
        | LPAREN expr RPAREN
        ;
