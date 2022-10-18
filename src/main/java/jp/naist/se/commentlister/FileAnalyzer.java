package jp.naist.se.commentlister;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.commentlister.reader.CommentReader;
import jp.naist.se.commentlister.reader.FileType;

/**
 * This main class extracts comments from source files listed in command line arguments.
 * @param args specify source files
 */
public class FileAnalyzer {

	public static void main(String[] args) {
		try (JsonGenerator gen = new JsonFactory().createGenerator(System.out)) {
			gen.useDefaultPrettyPrinter();
			gen.writeStartObject();
			gen.writeObjectFieldStart("Files");
			for (String s: args) {
				File f = new File(s);
				processFile(gen, f.toPath());
			}
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void processFile(JsonGenerator gen, Path path) throws IOException {
		Files.walkFileTree(path, new FileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String filename = file.toString();
				FileType t = FileType.getFileType(filename);
				extractComments(gen, file, filename, t);
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				System.err.println(file.toString() + " is not readable");
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	public static void extractComments(JsonGenerator gen, Path path, String filename, FileType t) throws IOException {
		byte[] content = Files.readAllBytes(path);
		CommentReader comments = FileType.createCommentReader(t, content);
		if (comments == null) return;
		gen.writeObjectFieldStart(filename);
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
