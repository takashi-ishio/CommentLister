package jp.naist.se.commentlister.ruby;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;

public class CommentScanner  {

	public CommentScanner(byte[] source) {
		ScriptingContainer container = new ScriptingContainer();
		StringWriter w = new StringWriter();
        //container.setOutput(w);
    	try (FileReader f = new FileReader(new File("comment.rb"))) {
    		container.put("value", new String(source));
    		EmbedEvalUnit unit = container.parse(f, "comment.rb");
    		unit.run();
    	} catch(IOException e) { 
    		e.printStackTrace();
    	}
        System.out.println(w.toString());
        container.terminate();
    }
	
	
}
