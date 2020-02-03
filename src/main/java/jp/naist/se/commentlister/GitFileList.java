package jp.naist.se.commentlister;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;


/**
 * This main class counts the number of files in a repository.
 * Arguments: [-f pattern] specifies a wild card pattern like "*.java". 
 * The program count the number of files that match the pattern.
 * The program accepts multiple patterns.
 */
public class GitFileList implements AutoCloseable {

	private static final String ARG_TARGET = "-target=";
	private static final String ARG_FILE_PATTERN = "-f";
	
	/**
	 * Extract all comments from Git directories.
	 * @param args specify a directory and a tag.
	 */
	public static void main(String[] args) { 
		// usage
		if (args.length == 0) {
			System.err.println("Usage: path/to/.git [-f pattern] [-target=tag/commitId]");
			return;
		}

		// Default configuration
		File dir = null;
		String target = "HEAD";
		ArrayList<String> patterns = new ArrayList<>();
		for (int i=0; i<args.length; i++) {
			String arg = args[i];
			if (arg.startsWith(ARG_TARGET)) {
				target = arg.substring(ARG_TARGET.length());
			} else if (arg.equals(ARG_FILE_PATTERN)) {
				i++;
				if (i<args.length) {
					patterns.add(args[i]);
				}
			} else {
				try {
					dir = new File(arg).getCanonicalFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		try (GitFileList analyzer = new GitFileList(patterns)) {
			File gitDir = ensureGitDir(dir);
			if (gitDir != null) {
				analyzer.parseGitRepository(gitDir, target);
			} else {
				System.err.println(dir + " is not a git repository.");
			}
		} catch (IOException e) {
			 e.printStackTrace();
		}
	}

	private JsonGenerator gen;
	private ArrayList<Counter> counters;

	public GitFileList(ArrayList<String> patterns) throws IOException {
		counters = new ArrayList<>();
		for (String p: patterns) {
			counters.add(new Counter(p));
		}
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
	 * The method returns null if dir is not a directory. 
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
				return dir;
			}
		}
		return null;
	}
	
	/**
	 * Make a short git repository name ("myApp/.git" or "myApp.git").
	 */
	public static String makeRepoName(File gitDir) {
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
					RevTree tree = commit.getTree();
					
					try (RevWalk revForLastModified = new RevWalk(repo)) { // Reuse a single walk object for performance
						try (TreeWalk walk = new TreeWalk(repo)) {
							walk.addTree(tree);
							walk.setRecursive(true);
							while (walk.next()) {
								String path = new String(walk.getRawPath());
								int idx = path.indexOf('/');
								if (idx >= 0) path = path.substring(idx+1);
								for (Counter c: counters) {
									if (c.accept(path)) {
										c.increment();
									}
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
						}
					}
					
					gen.writeArrayFieldStart("FileCount");
					for (Counter c: counters) {
						gen.writeStartObject();
						gen.writeStringField("Pattern", c.getPattern());
						gen.writeNumberField("Count", c.getCount());
						gen.writeEndObject();
					}
					gen.writeEndArray();
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
	 * Translate epoch seconds (Git Commit Time) into an ISO-style string
	 * @param epoch
	 * @return
	 */
	private static String epochToISO(int epoch) {
		return Instant.ofEpochSecond(epoch).toString();		
	}
	
	
	/**
	 * Internal class to count the numbers of files
	 */
	private static class Counter {
		
		private String pattern;
		private WildcardFileFilter filter;
		private int value;
		
		public Counter(String pattern) {
			this.pattern = pattern;
			this.filter = new WildcardFileFilter(pattern);
		}
		
		public boolean accept(String filepath) {
			return filter.accept(null, filepath);
		}
		
		public String getPattern() {
			return pattern;
		}
		
		public void increment() {
			value++;
		}
		
		public int getCount() {
			return value;
		}
		
		
	}

}
