package jp.naist.se.commentlister.reader;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * This implementation is the basic logic reading comments from ANTLR lexer. 
 */
public class AntlrCommentReader implements CommentReader {

	private Lexer lexer;
	private Filter filter;
	private Token current;
	
	/**
	 * @param lexer
	 * @param filter defines a condition to select "comments" from lexer.
	 */
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
		/**
		 * A method to classify a token to comment and non-comment. 
		 * @param t is a token to be classified.
		 * @return Implementation should return true for comment tokens, false for non-comment tokens.
		 */
		public boolean accept(Token t);
	}
}
