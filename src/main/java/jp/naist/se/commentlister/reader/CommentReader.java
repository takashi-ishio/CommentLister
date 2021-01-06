package jp.naist.se.commentlister.reader;


/**
 * Common interface to read comments from files 
 */
public interface CommentReader {

	/**
	 * Proceed to the next comment.
	 * @return true if there exists a comment to be processed.
	 * False is returned if the reader reached the end of file.
	 */
	public boolean next();
	
	/**
	 * @return the text of the current comment.
	 */
	public String getText();
	
	/**
	 * @return the line number of the comment location.
	 */
	public int getLine();
	
	/**
	 * @return the character position in the line.
	 */
	public int getCharPositionInLine();

}
