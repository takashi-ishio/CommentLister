package expref;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

public class CommentReader {

	private Lexer lexer;
	private Filter filter;
	
	public CommentReader(Lexer lexer, Filter filter) {
		this.lexer = lexer;
		this.filter = filter;
	}
	
	public Token nextToken() {
		Token t = lexer.nextToken();
		while (!filter.accept(t) && t.getType() != Lexer.EOF) {
			t = lexer.nextToken();
		}
		return t;
	}
	
	public static interface Filter {
		public boolean accept(Token t);
	}
}
