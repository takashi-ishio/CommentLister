package expref;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.antlr.v4.runtime.Token;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;



public class GitAnalyzer implements AutoCloseable {

	/**
	 * Extract comments from Git directories.
	 * @param args specify directories.
	 */
	public static void main(String[] args) { 
		try (GitAnalyzer analyzer = new GitAnalyzer()) {
			for (String arg: args) {
				File dir = new File(arg);
				File gitDir = ensureGitDir(dir);
				if (gitDir != null) {
					analyzer.parseGitRepository(gitDir);
				}
			}
		} catch (IOException e) {
			 e.printStackTrace();
		}
	}

	private JsonGenerator gen;

	public GitAnalyzer() throws IOException {
		gen = new JsonFactory().createGenerator(System.out);
		gen.useDefaultPrettyPrinter();
		gen.writeStartObject();
	}
	
	@Override
	public void close() {
		try {
			gen.close();
		} catch (IOException e) {
		}
	}
	
	/**
	 * Check whether a specified directory is .git directory or not.   
	 * @param dir
	 * @return dir itself if it is a .git directory.
	 * If it includes .git as a subdirectory, the subdirectory is returned.  
	 * The method returns null if dir is not .git directory. 
	 */
	public static File ensureGitDir(File dir) {
		if (dir.isDirectory()) {
			if (dir.getName().equals(".git") || dir.getName().endsWith(".git")) {
				return dir;
			} else {
				File another = new File(dir, ".git");
				if (another.exists() && another.isDirectory()) {
					return another;
				}
				File[] candidates = dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(".git");
					}
				});
				if (candidates.length > 0) {
					return candidates[0];
				}
			}
		}
		return null;
	}
	
	private String makeRepoName(File gitDir) {
		if (gitDir.getName().equals(".git")) {
			return gitDir.getParentFile().getName() + "/.git";
		} else {
			return gitDir.getName();
		}
	}

	public void parseGitRepository(File gitDir) {
		File dir = ensureGitDir(gitDir);
		if (dir == null) return;

		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			gen.writeObjectFieldStart(makeRepoName(gitDir));
			gen.writeObjectFieldStart("HEAD");
//			try (Git git = new Git(repo)) {
//				List<Ref> tags = git.tagList().call();
//				for (Ref tag: tags) {
					//startTag(tag);
					try (RevWalk rev = new RevWalk(repo)) {
						RevTree tree = rev.parseTree(repo.resolve("HEAD"));
						try (TreeWalk walk = new TreeWalk(repo)) {
							walk.addTree(tree);
							walk.setRecursive(true);
							while (walk.next()) {
								processFile(repo, walk.getRawPath(), walk.getObjectId(0));
							}
						}
					} catch (IncorrectObjectTypeException e) {
						e.printStackTrace();
						// A tag may be assigend to a file.
						// Ignore the exception to process the other tags.
					}
//				}
//			} catch (GitAPIException e) {
//				e.printStackTrace();
//			}
			gen.writeEndObject();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void processFile(Repository repo, byte[] path, ObjectId obj) throws IOException {
		String filename = new String(path);
		if (FileType.isSupported(filename)) {
			ObjectLoader reader = repo.newObjectReader().open(obj); 
			byte[] content = reader.getCachedBytes();
			FileType t = FileType.getFileType(filename);
			CommentReader lexer = FileType.createCommentReader(t, content);
			if (lexer == null) return;
			int counter = 0;
			gen.writeObjectFieldStart(filename);
			for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
				gen.writeObjectFieldStart(Integer.toString(counter++));
				gen.writeObjectField("Text", token.getText());
				gen.writeObjectField("Line", token.getLine());
				gen.writeObjectField("CharPositionInLine", token.getCharPositionInLine());
				gen.writeEndObject();
			}
			gen.writeEndObject();
		}
	}

}
