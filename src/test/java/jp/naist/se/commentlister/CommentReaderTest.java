package jp.naist.se.commentlister;


import org.junit.Assert;
import org.junit.Test;

public class CommentReaderTest {

	private int countComments(FileType t, String s) {
		CommentReader r = FileType.createCommentReader(t, s.getBytes());
		Assert.assertNotNull(r);
		int i = 0;
		while (r.next()) {
			i++;
		}
		return i;
	}
	
	@Test
	public void testCpp() {
		Assert.assertEquals(1, countComments(FileType.CPP, "// 1st line\n// 2nd line"));
		Assert.assertEquals(1, countComments(FileType.CPP, "/* 1st line */\n/* 2nd line */"));
		Assert.assertEquals(1, countComments(FileType.CPP, "/* 1st line */\n// 2nd line"));
		Assert.assertEquals(2, countComments(FileType.CPP, "/* 1st \n 2nd line */\n// 2nd comment"));
		Assert.assertEquals(1, countComments(FileType.CPP, "int x = 0; //1st line\nint y = 0; //2nd line"));
	}
	
	@Test
	public void testJava() {
		Assert.assertEquals(1, countComments(FileType.JAVA, "// 1st line\n// 2nd line"));
		Assert.assertEquals(1, countComments(FileType.JAVA, "/* 1st line */\n/* 2nd line */"));
		Assert.assertEquals(1, countComments(FileType.JAVA, "/* 1st line */\n// 2nd line"));
		Assert.assertEquals(2, countComments(FileType.JAVA, "/* 1st \n 2nd line */\n// 2nd comment"));
		Assert.assertEquals(1, countComments(FileType.JAVA, "int x = 0; //1st line\nint y = 0; //2nd line"));
	}
	
	@Test
	public void testPHP() {
		Assert.assertEquals(4, countComments(FileType.PHP, "<?php\necho '';\n//1st\n//2nd line\n  /* 2nd comment */\n   # 3rd comment\n?><HTML><!-- 4th Comment --></HTML>"));
	}
	
	@Test
	public void testPython() {
		Assert.assertEquals(2, countComments(FileType.PYTHON, "# 1st line\n# 2nd line\n # 2nd commnent"));
		Assert.assertEquals(2, countComments(FileType.PYTHON, "\"\"\"docString\n2nd line\n3rd line\n\"\"\"\n\"\"\"Another doc string\n\"\"\""));
	}
	
	@Test
	public void testRuby() throws Exception {
		Assert.assertEquals(1, countComments(FileType.RUBY, "# 1st line\n# 2nd line"));
		Assert.assertEquals(1, countComments(FileType.RUBY, "=begin 1st line \n=end"));
		Assert.assertEquals(2, countComments(FileType.RUBY, "=begin\n 1st comment \n=end\n=begin\n 2nd comment \n=end"));
		Assert.assertEquals(1, countComments(FileType.RUBY, "if x then \n puts \"a\" # a \n end"));
		Assert.assertEquals(1, countComments(FileType.RUBY, "if x then \n  puts \"a\" # 1st \n  puts \"b\" # 2nd \n end"));
	}
}
