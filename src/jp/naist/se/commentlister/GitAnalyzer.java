package jp.naist.se.commentlister;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.Token;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.MaxCountRevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

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
			File dir = new File(args[0]).getCanonicalFile();
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
	private HashMap<FileType, Counter> counters;

	public GitAnalyzer() throws IOException {
		counters = new HashMap<>();
		gen = new JsonFactory().createGenerator(System.out);
		gen.useDefaultPrettyPrinter();
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
	
	/**
	 * Make a short git repository name ("myApp/.git" or "myApp.git").
	 */
	private String makeRepoName(File gitDir) {
		if (gitDir.getName().equals(".git")) {
			return gitDir.getParentFile().getName() + "/.git";
		} else {
			return gitDir.getName();
		}
	}

	/**
	 * @param gitDir is a .git directory.
	 * @param target is a revision.
	 */
	public void parseGitRepository(File gitDir, String target) {
		File dir = ensureGitDir(gitDir);
		if (dir == null) return;

		long startTime = System.currentTimeMillis();
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			try (RevWalk rev = new RevWalk(repo)) {
				AnyObjectId objId = repo.resolve(target);
				if (objId != null) {
					RevCommit commit = rev.parseCommit(objId);
					gen.writeStartObject();
					gen.writeStringField("Repository", makeRepoName(gitDir));
					gen.writeStringField("Revision", target);
					gen.writeStringField("ObjectId", commit.getId().name());
					gen.writeStringField("CommitTime", epochToISO(commit.getCommitTime()));
					gen.writeObjectFieldStart("Files");
					RevTree tree = commit.getTree();
					
					try (RevWalk revForLastModified = new RevWalk(repo)) { // Reuse a single walk object for performance
						try (TreeWalk walk = new TreeWalk(repo)) {
							walk.addTree(tree);
							walk.setRecursive(true);
							while (walk.next()) {
								String path = new String(walk.getRawPath());
								if (FileType.isSupported(path)) {
									int lastModified =  lastModified(revForLastModified, repo, objId, path);
									processFile(repo, path, walk.getObjectId(0), lastModified);
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							gen.writeEndObject();
						}
					}
					gen.writeObjectFieldStart("FileTypes");
					for (Map.Entry<FileType, Counter> entry: counters.entrySet()) {
						gen.writeNumberField(entry.getKey().name(), entry.getValue().getCount());
					}
					gen.writeEndObject();
					gen.writeNumberField("ElapsedTime", System.currentTimeMillis() - startTime);
					gen.writeEndObject();
				} else {
					System.err.println("Error: " + target + " is not a commit ID.");
				}
			} catch (IncorrectObjectTypeException e) {
				System.err.println("Error: " + target + " is not a revision.");
			} catch (AmbiguousObjectException e) {
				System.err.println("Error: " + target + " is not unique in the repository.");
			} catch (RevisionSyntaxException e) {
				System.err.println("Error: " + target + " is not a valid revision.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compute the last modified time of a given file path in a repository.
	 * The implementation extracts the latest commit that modifies the specified file.  
	 * @return the seconds from epoch time.  0 if the time is unavailable.
	 */
	private int lastModified(RevWalk rev, Repository repo, AnyObjectId target, String path) {
		try {
			rev.reset();
			rev.markStart(repo.parseCommit(target));
			rev.setTreeFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF, 
					                               PathFilter.create(path)));
			rev.setRevFilter(MaxCountRevFilter.create(1));
			for (RevCommit c: rev) {
				return c.getCommitTime();
			}
			return 0;
		} catch (IOException e) {
			return 0;
		}
	}
	
	/**
	 * Translate epoch seconds (Git Commit Time) into an ISO-style string
	 * @param epoch
	 * @return
	 */
	private static String epochToISO(int epoch) {
		return Instant.ofEpochSecond(epoch).toString();		
	}
	
	public void processFile(Repository repo, String path, ObjectId obj, int lastModified) throws IOException {
		ObjectLoader reader = repo.newObjectReader().open(obj); 
		byte[] content = reader.getCachedBytes();
		FileType t = FileType.getFileType(path);
		CommentReader lexer = FileType.createCommentReader(t, content);
		if (lexer == null) return;
		counters.computeIfAbsent(t, type -> new Counter()).increment();
		gen.writeObjectFieldStart(path);
		gen.writeStringField("ObjectId", obj.name());
		gen.writeStringField("LastModified", epochToISO(lastModified));
		gen.writeStringField("FileType", t.name());
		int commentCount = 0;
		for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
			gen.writeObjectFieldStart(Integer.toString(commentCount++));
			gen.writeObjectField("Text", token.getText());
			gen.writeObjectField("Line", token.getLine());
			gen.writeObjectField("CharPositionInLine", token.getCharPositionInLine());
			gen.writeEndObject();
		}
		gen.writeNumberField("CommentCount", commentCount);
		gen.writeEndObject();
	}
	
	
	/**
	 * Internal class to count the numbers of files
	 */
	private static class Counter {
		
		private int value;
		
		public void increment() {
			value++;
		}
		
		public int getCount() {
			return value;
		}
	}

}
