package jp.naist.se.commentlister;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;

import org.antlr.v4.runtime.CaseChangingCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.IOUtils;

import jp.naist.se.commentlister.lexer.CMakeLexer;
import jp.naist.se.commentlister.lexer.CPP14Lexer;
import jp.naist.se.commentlister.lexer.CSharpLexer;
import jp.naist.se.commentlister.lexer.ECMAScriptLexer;
import jp.naist.se.commentlister.lexer.Java8Lexer;
import jp.naist.se.commentlister.lexer.MakefileCommentLexer;
import jp.naist.se.commentlister.lexer.PhpLexer;
import jp.naist.se.commentlister.lexer.Python3Lexer;
import jp.naist.se.commentlister.ruby.RubyCommentReader;


public enum FileType {

	UNSUPPORTED, CPP, JAVA, ECMASCRIPT, CSHARP, PYTHON, PHP, RUBY, CMAKE, CMAKESOURCE, QMAKE, MAKEFILE, AUTOMAKE, BAZEL, ANT, MAVEN;

	private static HashMap<String, FileType> filetype = new HashMap<>(64);
	private static HashMap<String, FileType> specialFileNames = new HashMap<>();
	private static HashMap<String, FileType> typeNames = new HashMap<>();
	
	static {
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

		filetype.put("cmake", FileType.CMAKE);	
		filetype.put("pro", FileType.QMAKE);	
		filetype.put("pri", FileType.QMAKE);
		filetype.put("bzl", FileType.BAZEL);

		specialFileNames.put("CMakeLists.txt", FileType.CMAKE);	
		specialFileNames.put("Makefile", FileType.MAKEFILE);	
		specialFileNames.put("Makefile.am", FileType.AUTOMAKE);	
		specialFileNames.put("BUILD", FileType.BAZEL);	
		specialFileNames.put("pom.xml", FileType.MAVEN);
		specialFileNames.put("build.xml", FileType.ANT);

		typeNames.put("cpp", FileType.CPP);
		typeNames.put("java", FileType.JAVA);
		typeNames.put("ecmascript", FileType.ECMASCRIPT);
		typeNames.put("csharp", FileType.CSHARP);
		typeNames.put("python", FileType.PYTHON);
		typeNames.put("php", FileType.PHP);
		typeNames.put("ruby", FileType.RUBY);
		typeNames.put("automake", FileType.AUTOMAKE);
		typeNames.put("bazel", FileType.BAZEL);
		typeNames.put("ant", FileType.ANT);
		typeNames.put("maven", FileType.MAVEN);
		typeNames.put("cmake", FileType.CMAKE);
		typeNames.put("cmakesource", FileType.CMAKESOURCE);
		typeNames.put("qmake", FileType.QMAKE);
		typeNames.put("makefile", FileType.MAKEFILE);
	}
	
	/**
	 * This is a set of analyzed files by default
	 */
	public static HashSet<FileType> getAllTypes() {
		HashSet<FileType> types = new HashSet<>();
		types.addAll(typeNames.values());
		return types;
	}

	
	public static FileType getFileType(String filename) {
		// Remove directories 
		int index = filename.lastIndexOf('/');
		filename = filename.substring(index+1);
		
		if (filename.startsWith("._")) { // Mac OS's backup file
			return FileType.UNSUPPORTED;
		}
		
		// Check special names
		FileType t = specialFileNames.get(filename);
		if (t != null) return t;
		
		// Check extensions
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
			// Check ".h.cmake" files because they are CMAKE-related but the grammar is C.
			if (type == FileType.CMAKE) {
				String trimmed = filename.substring(0, index);
				if (getFileType(trimmed) == FileType.CPP) {
					return FileType.CMAKESOURCE;
				}
			}
			
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
	
	private static CommentReader createReader(FileType filetype, CharStream stream) {
		switch (filetype) {
		case JAVA:
		{
			Java8Lexer lexer = new Java8Lexer(stream);
			return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return t.getChannel() == Java8Lexer.HIDDEN;
				}
			});
		}
		case CPP:
		case CMAKESOURCE:
		{
			CPP14Lexer lexer = new CPP14Lexer(stream);
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
			ECMAScriptLexer lexer = new ECMAScriptLexer(stream);
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
			CSharpLexer lexer = new CSharpLexer(stream);
			return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return t.getChannel() == CSharpLexer.COMMENTS_CHANNEL;
				}
			});
		}
		case PYTHON:
		case BAZEL:
		{
			Python3Lexer lexer = new Python3Lexer(stream);
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
			PhpLexer lexer = new PhpLexer(new CaseChangingCharStream(stream, false));
			return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return t.getChannel() == PhpLexer.PhpComments;
				}
			});
		}
		case MAKEFILE:
		case AUTOMAKE:
		case QMAKE:
		{
			MakefileCommentLexer lexer = new MakefileCommentLexer(stream);
			return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return t.getType() == MakefileCommentLexer.Line_comment;
				}
			});
		}
		case CMAKE:
		{
			CMakeLexer lexer = new CMakeLexer(stream);
			return new AntlrMultilineCommentReader(lexer, new AntlrCommentReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return (t.getType() == CMakeLexer.Bracket_comment ||
							t.getType() == CMakeLexer.Line_comment);
				}
			});
		}
		case ANT:
		case MAVEN:
		case RUBY:
		{
			assert false: "Unsupported language for this method";
		}
		default:
			return null;
		
		}
	}

	/**
	 * This method is prepared to handle "LargeObject" in a git repository.
	 * This method currently does not support Ruby files.
	 * @param filetype
	 * @param stream
	 * @return
	 */
	public static CommentReader createCommentReader(FileType filetype, InputStream stream) {
		try {
			if (filetype == FileType.RUBY) {
				return new RubyCommentReader(IOUtils.toByteArray(stream));
			} else if (filetype == FileType.ANT || filetype == FileType.MAVEN) {
				return new XmlCommentReader(stream);
			}
			return createReader(filetype, CharStreams.fromStream(stream));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static CommentReader createCommentReader(FileType filetype, byte[] buf) {
		try {
			if (filetype == FileType.RUBY) {
				return new RubyCommentReader(buf);
			} else if (filetype == FileType.ANT || filetype == FileType.MAVEN) {
				return new XmlCommentReader(new ByteArrayInputStream(buf));
			} else {
				return createReader(filetype, createStream(buf));
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * @param args
	 * @return file types to be analyzed
	 */
	public static HashSet<FileType> getFileTypes(String[] args) {
		HashSet<FileType> types = new HashSet<>();
		for (String arg: args) {
			FileType t = getFileType(arg);
			if (isSupported(t)) {
				types.add(t);
			}
			t = typeNames.get(arg.toLowerCase());
			if (isSupported(t)) {
				types.add(t);
			}
			t = specialFileNames.get(arg);
			if (isSupported(t)) {
				types.add(t);
			}
		}
		return types;
	}
	

}
