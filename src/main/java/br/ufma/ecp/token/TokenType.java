package br.ufma.ecp.token;

import java.util.List;

public enum TokenType {
    // symbols
    LPAREN, RPAREN,
    LBRACE, RBRACE,
    LBRACKET,RBRACKET,
    COMMA, SEMICOLON, DOT,
    PLUS,  MINUS,ASTERISK, SLASH,
    AND, OR, NOT,
    LT, GT, EQ,

     // Literals.
     NUMBER,
     STRING,


     IDENT,


     // keywords
     METHOD, WHILE, IF, CLASS,
     CONSTRUCTOR, FUNCTION, FIELD,
     STATIC, VAR, INT, CHAR,
     BOOLEAN, VOID, TRUE, FALSE,
     NULL, THIS, LET, DO, ELSE, RETURN,

     EOF,

     ILLEGAL;

    private TokenType() {
    }

     static public boolean isSymbol (char c) {
        String symbols = "{}()[].,;+-*/&|<>=~";
        return symbols.indexOf(c) > -1;
    }

    private TokenType(String value) {
        this.value = value;
    }

    public String value;

    static public boolean isKeyword (TokenType type) {
        List<TokenType> keywords  = 
            List.of(
                TokenType.METHOD,
                TokenType.WHILE,
                TokenType.IF,
                TokenType.CLASS,
                TokenType.CONSTRUCTOR,
                TokenType.FUNCTION,
                TokenType.FIELD,
                TokenType.STATIC,
                TokenType.VAR,
                TokenType.INT,
                TokenType.CHAR,
                TokenType.BOOLEAN,
                TokenType.VOID,
                TokenType.TRUE,
                TokenType.FALSE,
                TokenType.NULL,
                TokenType.THIS,
                TokenType.LET,
                TokenType.DO,
                TokenType.ELSE,
                TokenType.RETURN
            );
            return keywords.contains(type);
    }

}
