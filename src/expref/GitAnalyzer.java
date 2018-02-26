package expref;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.Instant;

import org.antlr.v4.runtime.Token;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
		if (args.length == 0 || args.length > 2) {
			System.err.println("Usage: path/to/.git [tag/commitId]");
			return;
		}
		try (GitAnalyzer analyzer = new GitAnalyzer()) {
			File dir = new File(args[0]);
			String target = (args.length == 2) ? args[1]: "HEAD";
			File gitDir = ensureGitDir(dir);
			if (gitDir != null) {
				analyzer.parseGitRepository(gitDir, target);
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

	public void parseGitRepository(File gitDir, String target) {
		File dir = ensureGitDir(gitDir);
		if (dir == null) return;

		long startTime = System.currentTimeMillis();
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			try (Git git = new Git(repo)) {
				try (RevWalk rev = new RevWalk(repo)) {
					RevCommit commit = rev.parseCommit(repo.resolve(target));
					gen.writeStringField("Repository", makeRepoName(gitDir));
					gen.writeStringField("Revision", target);
					gen.writeStringField("ObjectId", commit.getId().name());
					gen.writeStringField("CommitTime", epochToISO(commit.getCommitTime()));
					gen.writeObjectFieldStart("Files");
					RevTree tree = commit.getTree();
					try (TreeWalk walk = new TreeWalk(repo)) {
						walk.addTree(tree);
						walk.setRecursive(true);
						while (walk.next()) {
							String path = new String(walk.getRawPath());
							if (FileType.isSupported(path)) {
								int lastModified = lastModified(git, path);
								processFile(repo, path, walk.getObjectId(0), lastModified);
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						gen.writeEndObject();
					}
					gen.writeNumberField("ElapsedTime", System.currentTimeMillis() - startTime);
				} catch (IncorrectObjectTypeException e) {
					System.err.println("Error: " + target + " is not a revision.");
					// A tag may be assigend to a file.
					// Ignore the exception to process the other tags.
				} catch (AmbiguousObjectException e) {
					System.err.println("Error: " + target + " is not unique in the repository.");
				} catch (RevisionSyntaxException e) {
					System.err.println("Error: " + target + " is not a valid revision.");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Compute the last modified time of a given file path in a repository. 
	 * @return the seconds from epoch time.  0 if the time is unavailable.
	 */
	private int lastModified(Git git, String path) {
		try {
			int lastModified = 0;
			for (RevCommit c: git.log().addPath(path).call()) {
				lastModified = Math.max(lastModified, c.getCommitTime());
			}
			return lastModified;
		} catch (GitAPIException e) {
			return 0;
		}
	}
	
	private static String epochToISO(int epoch) {
		return Instant.ofEpochSecond(epoch).toString();		
	}
	
	public void processFile(Repository repo, String path, ObjectId obj, int lastModified) throws IOException {
		ObjectLoader reader = repo.newObjectReader().open(obj); 
		byte[] content = reader.getCachedBytes();
		FileType t = FileType.getFileType(path);
		CommentReader lexer = FileType.createCommentReader(t, content);
		if (lexer == null) return;
		int counter = 0;
		gen.writeObjectFieldStart(path);
		gen.writeStringField("ObjectId", obj.name());
		gen.writeStringField("lastModified", epochToISO(lastModified));
		int commentCount = 0;
		for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
			gen.writeObjectFieldStart(Integer.toString(counter++));
			gen.writeObjectField("Text", token.getText());
			gen.writeObjectField("Line", token.getLine());
			gen.writeObjectField("CharPositionInLine", token.getCharPositionInLine());
			gen.writeEndObject();
			commentCount++;
		}
		gen.writeNumberField("CommentCount", commentCount);
		gen.writeEndObject();
	}

}