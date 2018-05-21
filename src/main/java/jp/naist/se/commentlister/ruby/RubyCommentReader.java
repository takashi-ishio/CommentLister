package jp.naist.se.commentlister.ruby;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;

import jp.naist.se.commentlister.CommentReader;

public class RubyCommentReader implements CommentReader {

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
	private int index = 0;
	
	
	public RubyCommentReader(byte[] source) {
		ScriptingContainer container = new ScriptingContainer();
		
    	try (InputStreamReader f = new InputStreamReader(RubyCommentReader.class.getResourceAsStream("comment.rb"))) {
    		container.put("value", new String(source));
    		EmbedEvalUnit unit = container.parse(f, "comment.rb");
    		IRubyObject ret = unit.run();
    		comments = (Comment[])ret.toJava(Comment[].class);
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
