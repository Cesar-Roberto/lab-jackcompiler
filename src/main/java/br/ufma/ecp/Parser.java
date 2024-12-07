package br.ufma.ecp;

import static br.ufma.ecp.token.TokenType.DO;
import static br.ufma.ecp.token.TokenType.IF;
import static br.ufma.ecp.token.TokenType.LET;
import static br.ufma.ecp.token.TokenType.LPAREN;
import static br.ufma.ecp.token.TokenType.RETURN;
import static br.ufma.ecp.token.TokenType.WHILE;

import java.util.Arrays;

import br.ufma.ecp.token.Token;
import br.ufma.ecp.token.TokenType;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private Scanner scan;
    private Token currentToken;
    private Token peekToken;
    private StringBuilder xmlOutput = new StringBuilder();

    public Parser(byte[] input) {
        scan = new Scanner(input);
        nextToken();

    }

    public void parse() {
        expr();
    }

    private void nextToken() {
        currentToken = peekToken;
        peekToken = scan.nextToken();
    }

    public String XMLOutput() {
        return xmlOutput.toString();
    }

    private void printNonTerminal(String nterminal) {
        xmlOutput.append(String.format("<%s>\r\n", nterminal));
    }

    private void expectPeek(TokenType... types) {
        if (Arrays.stream(types).anyMatch(type -> peekToken.type == type)) {
            nextTokenAndAppend();
        } else {
            throw error(peekToken, "Expected one of: " + Arrays.toString(types));
        }
    }

    // Faz parte do expectPeek
    private void nextTokenAndAppend() {
        nextToken();
        xmlOutput.append(String.format("%s\r\n", currentToken));
    }

    boolean currentTokenIs(TokenType type) {
        return currentToken.type == type;
    }

    boolean peekTokenIs(TokenType type) {
        return peekToken.type == type;
    }

    private static void report(int line, String where,
            String message) {
        System.err.println(
                "[line " + line + "] Error" + where + ": " + message);
    }

    private ParseError error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
        return new ParseError();
    }

    // FALTA FINALIZAR
    void parseTerm() {
        printNonTerminal("term");
        switch (peekToken.type) {
            case NUMBER:
                expectPeek(TokenType.NUMBER);
                break;
            case STRING:
                expectPeek(TokenType.STRING);
                break;
            case FALSE:
            case NULL:
            case TRUE:
                expectPeek(TokenType.FALSE, TokenType.NULL, TokenType.TRUE);
                break;
            case THIS:
                expectPeek(TokenType.THIS);
                break;
            case IDENT:
                expectPeek(TokenType.IDENT);

                if (peekTokenIs(TokenType.LPAREN) || peekTokenIs(TokenType.DOT)) {
                    parseSubroutineCall();
                } else if (peekTokenIs(TokenType.LBRACKET)) {
                    expectPeek(TokenType.LBRACKET);
                    parseExpression();
                    expectPeek(TokenType.RBRACKET);
                }

                break;
            default:
                throw error(peekToken, "term expected");
        }

        printNonTerminal("/term");
    }

    static public boolean isOperator(String op) {
        return op != "" && "+-*/<>=~&|".contains(op);
    }

    void parseExpression() {
        printNonTerminal("expression");
        parseTerm();
        while (isOperator(peekToken.lexeme)) {
            expectPeek(peekToken.type);
            parseTerm();
        }
        printNonTerminal("/expression");
    }

    void parseLet() {
        printNonTerminal("letStatement");
        expectPeek(TokenType.LET);
        expectPeek(TokenType.IDENT);

        if (peekTokenIs(TokenType.LBRACKET)) {
            expectPeek(TokenType.LBRACKET);
            parseExpression();
            expectPeek(TokenType.RBRACKET);
        }

        expectPeek(TokenType.EQ);
        parseExpression();
        expectPeek(TokenType.SEMICOLON);
        printNonTerminal("/letStatement");
    }

    void parseSubroutineCall() {
        expectPeek(TokenType.IDENT);
        if (peekTokenIs(TokenType.LPAREN)) {
            expectPeek(TokenType.LPAREN);
            if (!peekTokenIs(TokenType.RPAREN)) {
                parseExpressionList();
            }
            expectPeek(TokenType.RPAREN);
        } else {
            expectPeek(TokenType.DOT);
            expectPeek(TokenType.IDENT);
            expectPeek(TokenType.LPAREN);
            if (!peekTokenIs(TokenType.RPAREN)) {
                parseExpressionList();
            }
            expectPeek(TokenType.RPAREN);
        }

        expectPeek(TokenType.LPAREN);
        expectPeek(TokenType.RPAREN);
    }

    void parseExpressionList() {
        printNonTerminal("expressionList");

        if (!peekTokenIs(TokenType.RPAREN)) {
            parseExpression();
        }

        while (peekTokenIs(TokenType.COMMA)) {
            expectPeek(TokenType.COMMA);
            parseExpression();
        }

        printNonTerminal("/expressionList");
    }

    public void parseDo() {
        printNonTerminal("doStatement");
        expectPeek(TokenType.DO);
        parseSubroutineCall();
        expectPeek(TokenType.SEMICOLON);
        printNonTerminal("/doStatement");
    }

    public void parseIf() {
        printNonTerminal("ifStatement");
        expectPeek(TokenType.IF);
        expectPeek(TokenType.LPAREN);
        parseExpression();
        expectPeek(TokenType.RPAREN);
        expectPeek(TokenType.LBRACE);
        parseStatements();
        expectPeek(TokenType.RBRACE);
        if (peekTokenIs(TokenType.ELSE)) {
            expectPeek(TokenType.ELSE);
            expectPeek(TokenType.LBRACE);
            parseStatements();
            expectPeek(TokenType.RBRACE);
        }
        printNonTerminal("/ifStatement");
    }

    public void parseWhile() {
        printNonTerminal("whileStatement");
        expectPeek(TokenType.WHILE);
        expectPeek(TokenType.LPAREN);
        parseExpression();
        expectPeek(TokenType.RPAREN);
        expectPeek(TokenType.LBRACE);
        parseStatements();
        expectPeek(TokenType.RBRACE);
        printNonTerminal("/whileStatement");
    }

    public void parseReturn() {
        printNonTerminal("returnStatement");
        expectPeek(TokenType.RETURN);
        if (peekTokenIs(TokenType.SEMICOLON)) {
            expectPeek(TokenType.SEMICOLON);
            printNonTerminal("/returnStatement");
        } else {
            parseExpression();
            expectPeek(TokenType.SEMICOLON);
            printNonTerminal("/returnStatement");
        }
    }

    public void parseStatement() {
        switch (peekToken.type) {
            case LET:
                parseLet();
                break;
            case WHILE:
                parseWhile();
                break;
            case IF:
                parseIf();
                break;
            case RETURN:
                parseReturn();
                break;
            case DO:
                parseDo();
                break;
            default:
                throw error(peekToken, "Expected a statement");
        }
    }

    public void parseStatements() {
        printNonTerminal("statements");
        while (peekToken.type == WHILE ||
                peekToken.type == IF ||
                peekToken.type == LET ||
                peekToken.type == DO ||
                peekToken.type == RETURN) {
            parseStatement();
        }

        printNonTerminal("/statements");
    }

    void expr() {
        number();
        oper();
    }

    void number() {
        System.out.println(currentToken.lexeme);
        match(TokenType.NUMBER);
    }

    private void match(TokenType t) {
        if (currentToken.type == t) {
            nextToken();
        } else {
            throw new Error("syntax error");
        }
    }

    void oper() {
        if (currentToken.type == TokenType.PLUS) {
            match(TokenType.PLUS);
            number();
            System.out.println("add");
            oper();
        } else if (currentToken.type == TokenType.MINUS) {
            match(TokenType.MINUS);
            number();
            System.out.println("sub");
            oper();
        } else if (currentToken.type == TokenType.LT) {
            match(TokenType.LT);
            number();
            System.out.println("lt");
            oper();
        } else if (currentToken.type == TokenType.GT) {
            match(TokenType.GT);
            number();
            System.out.println("gt");
            oper();
        } else if (currentToken.type == TokenType.EQ) {
            match(TokenType.EQ);
            number();
            System.out.println("eq");
            oper();
        } else if (currentToken.type == TokenType.AND) {
            match(TokenType.AND);
            number();
            System.out.println("and");
            oper();
        } else if (currentToken.type == TokenType.OR) {
            match(TokenType.OR);
            number();
            System.out.println("or");
            oper();
        } else if (currentToken.type == TokenType.EOF) {
            // vazio
        } else {
            throw new Error("syntax error");
        }
    }

    public String VMOutput() {
        return "";
    }

}
