package br.ufma.ecp.token;
public class Token {

    public final TokenType type;
    public final String lexeme;
    public final int line;

    public Token (TokenType type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    public String value () {
        return type.value;
    }

    public String toString() {
        var type = this.type.toString();
        String valor = lexeme;
        if (type.equals("NUMBER"))
            type =  "integerConstant";

        if (type.equals("STRING"))
            type =  "stringConstant";

        if (type.equals("IDENT"))
            type =  "identifier";

        if (TokenType.isSymbol(lexeme.charAt(0)))
            type = "symbol";

            if (valor.equals(">")) {
                valor = "&gt;" ;
            } else if (valor.equals("<")) {
                valor = "&lt;" ;
            } else if (valor.equals("\"")) {
                valor = "&quot;" ;
            } else if (valor.equals("&")) {
                valor = "&amp;" ;
            }

        if (TokenType.isKeyword(this.type) )
            type = "keyword";
    

        return "<"+ type +"> " + valor + " </"+ type + ">";
    }
    
}
