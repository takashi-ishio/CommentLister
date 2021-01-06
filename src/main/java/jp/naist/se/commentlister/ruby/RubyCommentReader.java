package jp.naist.se.commentlister.ruby;

import java.io.InputStreamReader;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;

import jp.naist.se.commentlister.reader.CommentReader;

/**
 * This class extracts comments from a ruby file using JRuby and a Ruby script with Ripper.
 * We did not use a grammar on ANTLR4 grammar repo because the grammar file did not 
 * support the full language grammar at that moment. 
 */
public class RubyCommentReader implements CommentReader {

	/**
	 * Internal object to record a comment 
	 */
	public static class Comment {
		private String text;
		private int line;
		private int charPositionInLine;
		
		public Comment(String t, int l, int charpos) {
			this.text = t;
			this.line = l;
			this.charPositionInLine = charpos;
		}
	}

	private Comment[] comments;
	private int index = -1;
	
	/**
	 * Read comments from a Ruby source file
	 * @param source is the content of the source file
	 */
	public RubyCommentReader(byte[] source) {
		ScriptingContainer container = new ScriptingContainer();
		
    	try (InputStreamReader f = new InputStreamReader(RubyCommentReader.class.getResourceAsStream("comment.rb"))) {
    		container.put("value", new String(source));
    		EmbedEvalUnit unit = container.parse(f, "comment.rb");
    		IRubyObject ret = unit.run();
    		if (ret != null && !ret.isNil()) {
        		comments = (Comment[])ret.toJava(Comment[].class);
    		} else {
        		comments = new Comment[0];
    		}
    	} catch(Throwable e) { 
    		e.printStackTrace();
    		comments = new Comment[0];
    	}
    }
	
	@Override
	public boolean next() {
		index++;
		return index < comments.length;
	}
	
	@Override
	public String getText() {
		return comments[index].text;
	}
	
	@Override
	public int getLine() {
		return comments[index].line;
	}
	
	@Override
	public int getCharPositionInLine() {
		return comments[index].charPositionInLine;
	}
	
}
