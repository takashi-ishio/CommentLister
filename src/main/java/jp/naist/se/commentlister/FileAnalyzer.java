package jp.naist.se.commentlister;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * For Debugging
 * @param args
 */
public class FileAnalyzer {

	public static void main(String[] args) {
		try (JsonGenerator gen = new JsonFactory().createGenerator(System.out)) {
			gen.useDefaultPrettyPrinter();
			gen.writeStartObject();
			gen.writeObjectFieldStart("Files");
			for (String s: args) {
				processFile(gen, s);
			}
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void processFile(JsonGenerator gen, String path) throws IOException {
		FileType t = FileType.getFileType(path);
		byte[] content = Files.readAllBytes(new File(path).toPath());
		CommentReader comments = FileType.createCommentReader(t, content);
		if (comments == null) return;
		gen.writeObjectFieldStart(path);
		gen.writeStringField("FileType", t.name());
		int commentCount = 0;
		while (comments.next()) {
			gen.writeObjectFieldStart(Integer.toString(commentCount++));
			gen.writeObjectField("Text", comments.getText());
			gen.writeObjectField("Line", comments.getLine());
			gen.writeObjectField("CharPositionInLine", comments.getCharPositionInLine());
			gen.writeEndObject();
		}
		gen.writeNumberField("CommentCount", commentCount);
		gen.writeEndObject();
	}

}
