package jp.naist.se.commentlister;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * List all files and their tokens for debugging
 * @param args specify source files
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
		File f = new File(path);
		if (!f.canRead()) {
			System.err.println(path + " is not readable");
			return;
		}
		byte[] content = Files.readAllBytes(f.toPath());
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
