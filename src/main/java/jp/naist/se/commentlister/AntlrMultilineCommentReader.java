package jp.naist.se.commentlister;

import java.util.ArrayList;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * Read multiple single-line comments in consecutive lines 
 * as a single multi-line comment. 
 */
public class AntlrMultilineCommentReader implements CommentReader {

	private static class Comment {
		private String text;
		private int line;
		private int endLine;
		private int charPositionInLine;
		
		public Comment(String t, int l, int charpos) {
			this.text = t;
			this.line = l;
			this.endLine = l;
			this.charPositionInLine = charpos;
		}
		
		public boolean isContinue(String t, int l, int charpos) {
			return (this.endLine == l) || (this.endLine + 1 == l && this.charPositionInLine == charpos);
		}
		
		public void append(String t, int l) {
			if (this.endLine == l) {
				this.text = this.text + " " + t;
			} else {
				this.text = this.text + "\n" + t;
				this.endLine = l;
			}
		}
		
		public String toString() {
			return text + " (line=" + line + ", endLine=" + endLine + ", charPos=" + charPositionInLine+ ")"; 
		}
	}

	private ArrayList<Comment> comments;
	private int index;
	
	public AntlrMultilineCommentReader(Lexer lexer, AntlrCommentReader.Filter filter) {
		this.comments = new ArrayList<>();
		this.index = -1;
		
		Comment lastComment = null;
		for (Token t = lexer.nextToken(); t.getType() != Lexer.EOF; t = lexer.nextToken()) {
			if (filter.accept(t)) {
				if (lastComment != null && lastComment.isContinue(t.getText(), t.getLine(), t.getCharPositionInLine())) {
					lastComment.append(t.getText(), t.getLine());
				} else {
					lastComment = new Comment(t.getText(), t.getLine(), t.getCharPositionInLine());
					comments.add(lastComment);
				}
			}
		}
	}
	
	@Override
	public boolean next() {
		index++;
		return index < comments.size();
	}
	
	@Override
	public String getText() {
		return comments.get(index).text;
	}
	
	@Override
	public int getLine() {
		return comments.get(index).line;
	}
	
	@Override
	public int getCharPositionInLine() {
		return comments.get(index).charPositionInLine;
	}

}
