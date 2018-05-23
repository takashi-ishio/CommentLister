package jp.naist.se.commentlister;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import org.antlr.v4.runtime.CaseChangingCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.CPP14Lexer;
import jp.naist.se.commentlister.lexer.CSharpLexer;
import jp.naist.se.commentlister.lexer.ECMAScriptLexer;
import jp.naist.se.commentlister.lexer.Java8Lexer;
import jp.naist.se.commentlister.lexer.PhpLexer;
import jp.naist.se.commentlister.lexer.Python3Lexer;
import jp.naist.se.commentlister.ruby.RubyCommentReader;


public enum FileType {

	UNSUPPORTED, CPP, JAVA, ECMASCRIPT, CSHARP, PYTHON, PHP, RUBY;

	private static HashMap<String, FileType> filetype;
	static {
		filetype = new HashMap<>(64);
		filetype.put("c", FileType.CPP);
		filetype.put("cc", FileType.CPP);
		filetype.put("cp", FileType.CPP);
		filetype.put("cpp", FileType.CPP);
		filetype.put("cx", FileType.CPP);
		filetype.put("cxx", FileType.CPP);
		filetype.put("c+", FileType.CPP);
		filetype.put("c++", FileType.CPP);
		filetype.put("h", FileType.CPP);
		filetype.put("hh", FileType.CPP);
		filetype.put("hxx", FileType.CPP);
		filetype.put("h+", FileType.CPP);
		filetype.put("h++", FileType.CPP);
		filetype.put("hp", FileType.CPP);
		filetype.put("hpp", FileType.CPP);

		filetype.put("java", FileType.JAVA);
		
		filetype.put("js", FileType.ECMASCRIPT);

		filetype.put("cs", FileType.CSHARP);

		filetype.put("py", FileType.PYTHON);

		filetype.put("php", FileType.PHP);
		
		filetype.put("rb", FileType.RUBY);
	}
	
	public static FileType getFileType(String filename) {
		// Remove directories 
		int index = filename.lastIndexOf("/");
		filename = filename.substring(index+1);
		
		if (filename.startsWith("._")) { // Mac OS's backup file
			return FileType.UNSUPPORTED;
		}
		
		index = filename.lastIndexOf('.');
		if (index < 0) {
			return FileType.UNSUPPORTED;
		}
		String ext = filename.substring(index + 1);
		FileType type = filetype.get(ext);
		if (type == null) {
			type = filetype.get(ext.toLowerCase());
		}
		if (type != null) {
			return type;
		}
		return FileType.UNSUPPORTED;
	}

	public static boolean isSupported(String filename) {
		return isSupported(getFileType(filename));
	}

	public static boolean isSupported(FileType filetype) {
		return filetype != FileType.UNSUPPORTED;
	}

	/**
	 * Create a stream for an ANTLR lexer.
	 * This method handles UTF-8/16 BOM.
	 * @param buf bytes be parsed.
	 * @return an instance of ANTLR CharStream.
	 * @throws IOException may be thrown if instantiation failed.
	 */
	private static CharStream createStream(byte[] buf) throws IOException {
		if (buf.length >= 3 && 
			buf[0] == (byte)0xEF && buf[1] == (byte)0xBB && buf[2] == (byte)0xBF) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 3, buf.length-3));
		} else if (buf.length >= 2 && buf[0] == (byte)0xFE && buf[1] == (byte)0xFF) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 2, buf.length-2), Charset.forName("UTF-16BE"));
		} else if (buf.length >= 2 && buf[0] == (byte)0xFF && buf[1] == (byte)0xFE) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 2, buf.length-2), Charset.forName("UTF-16LE"));
		} else {
			return CharStreams.fromStream(new ByteArrayInputStream(buf));
		}
	}
	
	public static CommentReader createCommentReader(FileType filetype, byte[] buf) {
		try {
			switch (filetype) {
			case JAVA:
			{
				Java8Lexer lexer = new Java8Lexer(createStream(buf));
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() == Java8Lexer.HIDDEN;
					}
				});
			}
			case CPP:
			{
				CPP14Lexer lexer = new CPP14Lexer(createStream(buf));
				//CommonTokenStream c = new CommonTokenStream(lexer, Java8Lexer.HIDDEN);
				//System.out.println(c.size());
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() == Java8Lexer.HIDDEN;
					}
				});
			}
			case ECMASCRIPT:
			{
				ECMAScriptLexer lexer = new ECMAScriptLexer(createStream(buf));
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() == ECMAScriptLexer.HIDDEN &&
								(t.getType() == ECMAScriptLexer.MultiLineComment ||
								t.getType() == ECMAScriptLexer.SingleLineComment);
					}
				});
			}
			case CSHARP:
			{
				CSharpLexer lexer = new CSharpLexer(createStream(buf));
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() == CSharpLexer.COMMENTS_CHANNEL;
					}
				});
			}
			case PYTHON:
			{
				Python3Lexer lexer = new Python3Lexer(createStream(buf));
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return (t.getChannel() == Python3Lexer.HIDDEN) ||
							(t.getType() == Python3Lexer.STRING && t.getText().contains("\"\"\""));
					}
				});
			}
			case PHP:
			{
				PhpLexer lexer = new PhpLexer(new CaseChangingCharStream(createStream(buf), false));
				return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() == PhpLexer.PhpComments;
					}
				});
			}
			case RUBY:
			{
				RubyCommentReader reader = new RubyCommentReader(buf);
				return reader;
			}
			default:
				return null;
			
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
