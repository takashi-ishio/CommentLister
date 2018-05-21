package jp.naist.se.commentlister;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

public class AntlrCommentReader implements CommentReader {

	private Lexer lexer;
	private Filter filter;
	private Token current;
	
	public AntlrCommentReader(Lexer lexer, Filter filter) {
		this.lexer = lexer;
		this.filter = filter;
	}
	
	@Override
	public boolean next() {
		current = lexer.nextToken();
		while (!filter.accept(current) && current.getType() != Lexer.EOF) {
			current = lexer.nextToken();
		}
		return current.getType() != Lexer.EOF;
	}
	
	@Override
	public String getText() {
		return current.getText();
	}
	
	@Override
	public int getLine() {
		return current.getLine();
	}
	
	@Override
	public int getCharPositionInLine() {
		return current.getCharPositionInLine();
	}
	
	public static interface Filter {
		public boolean accept(Token t);
	}
}
