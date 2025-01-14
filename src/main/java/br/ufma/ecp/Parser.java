package br.ufma.ecp;

import static br.ufma.ecp.token.TokenType.*;

import br.ufma.ecp.SymbolTable.Kind;
import br.ufma.ecp.SymbolTable.Symbol;
import br.ufma.ecp.VMWriter.Command;
import br.ufma.ecp.VMWriter.Segment;


import java.util.Arrays;

//import javax.swing.text.Segment;

import br.ufma.ecp.token.Token;
import br.ufma.ecp.token.TokenType;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private Scanner scan;
    private Token currentToken;
    private Token peekToken;
    private String className = "";
    private StringBuilder xmlOutput = new StringBuilder();
    private VMWriter vmWriter = new VMWriter();

    private int ifLabelNum = 0 ;
    private int whileLabelNum = 0;

    public Parser(byte[] input) {
        scan = new Scanner(input);
        nextToken();

    }

    public void parse() {
        parseClass();
    }

    void parseClass() {
        printNonTerminal("class");
        expectPeek(CLASS);
        expectPeek(IDENT);
        className = currentToken.lexeme;
        expectPeek(LBRACE);

        while (peekTokenIs(TokenType.STATIC) || peekTokenIs(TokenType.FIELD)) {
            parseClassVarDec();
        }

        while (peekTokenIs(TokenType.FUNCTION) || peekTokenIs(TokenType.CONSTRUCTOR) || peekTokenIs(TokenType.METHOD)) {
            parseSubroutineDec();
        }

        expectPeek(TokenType.RBRACE);

        printNonTerminal("/class");
    }

    private SymbolTable symTable = new SymbolTable();

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

    private Segment kind2Segment(Kind kind) {
        if (kind == Kind.STATIC)
            return Segment.STATIC;
        if (kind == Kind.FIELD)
            return Segment.THIS;
        if (kind == Kind.VAR)
            return Segment.LOCAL;
        if (kind == Kind.ARG)
            return Segment.ARG;
        return null;
    }

    void parseTerm() {
        printNonTerminal("term");
        switch (peekToken.type) {
            case NUMBER:
                expectPeek(TokenType.NUMBER);
                vmWriter.writePush(Segment.CONST, Integer.parseInt(currentToken.lexeme));
                break;
            case STRING:
                expectPeek(TokenType.STRING);
                var strValue = currentToken.lexeme;
                vmWriter.writePush(Segment.CONST, strValue.length());
                vmWriter.writeCall("String.new", 1);
                for (int i = 0; i < strValue.length(); i++) {
                    vmWriter.writePush(Segment.CONST, strValue.charAt(i));
                    vmWriter.writeCall("String.appendChar", 2);
                }
                break;
                case FALSE:
                case NULL:
                case TRUE:
                    expectPeek(FALSE, NULL, TRUE);
                    vmWriter.writePush(Segment.CONST, 0);
                    if (currentToken.type == TRUE)
                        vmWriter.writeArithmetic(Command.NOT);
                    break;
                case THIS:
                    expectPeek(THIS);
                    vmWriter.writePush(Segment.POINTER, 0);
                    break;
            case IDENT:
                expectPeek(TokenType.IDENT);

                Symbol sym = symTable.resolve(currentToken.lexeme);
                
                if (peekTokenIs(TokenType.LPAREN) || peekTokenIs(TokenType.DOT)) {
                    parseSubroutineCall();
                } else { 
                    if (peekTokenIs(TokenType.LBRACKET)) { 
                        expectPeek(TokenType.LBRACKET);
                        parseExpression();                        
                        expectPeek(TokenType.RBRACKET);                       
                    } else {
                        vmWriter.writePush(kind2Segment(sym.kind()), sym.index());
                    }
                }
                break;
            case LPAREN:
                expectPeek(TokenType.LPAREN);
                parseExpression();
                expectPeek(TokenType.RPAREN);
                break;
                case MINUS:
                case NOT:
                    expectPeek(MINUS, NOT);
                    var op = currentToken.type;
                    parseTerm();
                    if (op == MINUS)
                        vmWriter.writeArithmetic(Command.NEG);
                    else
                        vmWriter.writeArithmetic(Command.NOT);
        
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
            var ope = peekToken.type;
            expectPeek(peekToken.type);
            parseTerm();
            compileOperators(ope);
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
            parseExpressionList();
            expectPeek(TokenType.RPAREN);
        } else {
            expectPeek(TokenType.DOT);
            expectPeek(TokenType.IDENT);
            expectPeek(TokenType.LPAREN);
            parseExpressionList();
            expectPeek(TokenType.RPAREN);
        }
    }

    void parseExpressionList() {
        printNonTerminal("expressionList");

        // Chama parseExpression apenas se eu tiver uma expressão
        // a ser tratada
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

        var labelTrue = "IF_TRUE" + ifLabelNum;
        var labelFalse = "IF_FALSE" + ifLabelNum;
        var labelEnd = "IF_END" + ifLabelNum;

        ifLabelNum++;

        expectPeek(TokenType.IF);
        expectPeek(TokenType.LPAREN);
        parseExpression();
        expectPeek(TokenType.RPAREN);

        vmWriter.writeIf(labelTrue);
        vmWriter.writeGoto(labelFalse);
        vmWriter.writeLabel(labelTrue);

        expectPeek(TokenType.LBRACE);
        parseStatements();
        expectPeek(TokenType.RBRACE);
        if (peekTokenIs(ELSE)){
            vmWriter.writeGoto(labelEnd);
        }

        vmWriter.writeLabel(labelFalse);

        if (peekTokenIs(ELSE))
        {
            expectPeek(ELSE);
            expectPeek(LBRACE);
            parseStatements();
            expectPeek(RBRACE);
            vmWriter.writeLabel(labelEnd);
        }
        printNonTerminal("/ifStatement");
    }

    public void parseWhile() {
        printNonTerminal("whileStatement");

        var labelTrue = "WHILE_EXP" + whileLabelNum;
        var labelFalse = "WHILE_END" + whileLabelNum;
        whileLabelNum++;

        vmWriter.writeLabel(labelTrue);

        expectPeek(WHILE);
        expectPeek(LPAREN);
        parseExpression();

        vmWriter.writeArithmetic(Command.NOT);
        vmWriter.writeIf(labelFalse);

        expectPeek(RPAREN);
        expectPeek(LBRACE);
        parseStatements();

        vmWriter.writeGoto(labelTrue); 
        vmWriter.writeLabel(labelFalse); 

        expectPeek(RBRACE);
        printNonTerminal("/whileStatement");
    }
    
    public void parseReturn() {
        printNonTerminal("returnStatement");
        expectPeek(RETURN);
        if (!peekTokenIs(SEMICOLON)) {
            parseExpression();
        } else {
            vmWriter.writePush(Segment.CONST, 0);
        }
        expectPeek(SEMICOLON);
        vmWriter.writeReturn();

        printNonTerminal("/returnStatement");
    }
    

    void parseVarDec() {
        printNonTerminal("varDec");
        expectPeek(VAR);

        SymbolTable.Kind kind = Kind.VAR;

        expectPeek(INT, CHAR, BOOLEAN, IDENT);
        String type = currentToken.lexeme;

        expectPeek(IDENT);
        String name = currentToken.lexeme;
        symTable.define(name, type, kind);

        while (peekTokenIs(COMMA)) {
            expectPeek(COMMA);
            expectPeek(IDENT);

            name = currentToken.lexeme;
            symTable.define(name, type, kind);

        }

        expectPeek(SEMICOLON);
        printNonTerminal("/varDec");
    }

    void parseClassVarDec() {
        printNonTerminal("classVarDec");
        expectPeek(FIELD, STATIC);

        SymbolTable.Kind kind = Kind.STATIC;
        if (currentTokenIs(FIELD))
            kind = Kind.FIELD;

        expectPeek(INT, CHAR, BOOLEAN, IDENT);
        String type = currentToken.lexeme;

        expectPeek(IDENT);
        String name = currentToken.lexeme;

        symTable.define(name, type, kind);
        while (peekTokenIs(COMMA)) {
            expectPeek(COMMA);
            expectPeek(IDENT);

            name = currentToken.lexeme;
            symTable.define(name, type, kind);
        }

        expectPeek(SEMICOLON);
        printNonTerminal("/classVarDec");
    }


    void parseSubroutineDec() {
        printNonTerminal("subroutineDec");
    
        ifLabelNum = 0;
        whileLabelNum = 0;
    
        symTable.startSubroutine();
    
        expectPeek(CONSTRUCTOR, FUNCTION, METHOD);
        var subroutineType = currentToken.type;
    
        if (subroutineType == METHOD) {
            symTable.define("this", className, Kind.ARG);
        }
    
        expectPeek(VOID, INT, CHAR, BOOLEAN, IDENT); 
        expectPeek(IDENT);
    
        var functionName = className + "." + currentToken.lexeme;
    
        expectPeek(LPAREN);
        parseParameterList();
        expectPeek(RPAREN);
        parseSubroutineBody(functionName, subroutineType);
    
        printNonTerminal("/subroutineDec");
    }
    
    
      


    void parseParameterList() {
        printNonTerminal("parameterList");

        SymbolTable.Kind kind = Kind.ARG;

        if (!peekTokenIs(RPAREN)) // verifica se tem pelo menos uma expressao
        {
            expectPeek(INT, CHAR, BOOLEAN, IDENT);
            String type = currentToken.lexeme;

            expectPeek(IDENT);
            String name = currentToken.lexeme;
            symTable.define(name, type, kind);

            while (peekTokenIs(COMMA)) {
                expectPeek(COMMA);
                expectPeek(INT, CHAR, BOOLEAN, IDENT);
                type = currentToken.lexeme;

                expectPeek(IDENT);
                name = currentToken.lexeme;

                symTable.define(name, type, kind);
            }

        }

        printNonTerminal("/parameterList");
    }
    void parseSubroutineBody(String functionName, TokenType subroutineType) {

        printNonTerminal("subroutineBody");
        expectPeek(LBRACE);
        while (peekTokenIs(VAR)) {
            parseVarDec();
        }
				var nlocals = symTable.varCount(Kind.VAR);

        vmWriter.writeFunction(functionName, nlocals);

        parseStatements();
        expectPeek(RBRACE);
        printNonTerminal("/subroutineBody");
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
    public String VMOutput(){
        return vmWriter.vmOutput();
    }


    public void compileOperators(TokenType type) {

        if (type == ASTERISK) {
            vmWriter.writeCall("Math.multiply", 2);
        } else if (type == SLASH) {
            vmWriter.writeCall("Math.divide", 2);
        } else {
            vmWriter.writeArithmetic(typeOperator(type));
        }
    }

    private Command typeOperator(TokenType type) {
        if (type == PLUS)
            return Command.ADD;
        if (type == MINUS)
            return Command.SUB;
        if (type == LT)
            return Command.LT;
        if (type == GT)
            return Command.GT;
        if (type == EQ)
            return Command.EQ;
        if (type == AND)
            return Command.AND;
        if (type == OR)
            return Command.OR;
        return null;
    }


}
