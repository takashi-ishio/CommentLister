package jp.naist.se.commentlister.ruby;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;

import jp.naist.se.commentlister.CommentReader;

public class RubyCommentReader implements CommentReader {
	
	public RubyCommentReader(byte[] source) {
		ScriptingContainer container = new ScriptingContainer();
		StringWriter w = new StringWriter();
        //container.setOutput(w);
		
    	try (InputStreamReader f = new InputStreamReader(RubyCommentReader.class.getResourceAsStream("comment.rb"))) {
    		container.put("value", new String(source));
    		EmbedEvalUnit unit = container.parse(f, "comment.rb");
    		unit.run();
    	} catch(IOException e) { 
    		e.printStackTrace();
    	}
        System.out.println(w.toString());
        container.terminate();
    }
	
	@Override
	public boolean next() {
		return false;
	}
	
	@Override
	public String getText() {
		return null;
	}
	
	@Override
	public int getLine() {
		return 0;
	}
	
	@Override
	public int getCharPositionInLine() {
		return 0;
	}
	
}
