package jp.naist.se.commentlister;


public interface CommentReader {

	public boolean next();
	public String getText();
	public int getLine();
	public int getCharPositionInLine();

}
