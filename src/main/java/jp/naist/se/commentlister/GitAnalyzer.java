package jp.naist.se.commentlister;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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

	private static final String ARG_TARGET = "-target=";
	private static final String ARG_TYPE = "-type=";
	
	/**
	 * Extract all comments from Git directories.
	 * @param args specify a directory and a tag.
	 */
	public static void main(String[] args) { 
		
		// Default configuration
		File dir = null;
		String target = "HEAD";
		HashSet<FileType> types = FileType.getAllTypes();
		
		for (String arg: args) {
			if (arg.startsWith(ARG_TARGET)) {
				target = arg.substring(ARG_TARGET.length());
			} else if (arg.startsWith(ARG_TYPE)) {
				types = FileType.getFileTypes(arg.substring(ARG_TYPE.length()).split(","));
			} else {
				try {
					dir = new File(arg).getCanonicalFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (args.length == 0) {
			System.err.println("Usage: path/to/.git [-type=A,B,...] [-target=tag/commitId]");
			return;
		}
		try (GitAnalyzer analyzer = new GitAnalyzer()) {
			File gitDir = ensureGitDir(dir);
			if (gitDir != null) {
				analyzer.parseGitRepository(gitDir, target, types);
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
	public void parseGitRepository(File gitDir, String target, HashSet<FileType> types) {
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
								FileType t = FileType.getFileType(path);
								if (types.contains(t)) {
									int lastModified =  lastModified(revForLastModified, repo, objId, path);
									processFile(repo, path, t, walk.getObjectId(0), lastModified);
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							gen.writeEndObject();
						}
					}
					gen.writeObjectFieldStart("FileTypes");
					
					ArrayList<FileType> keys = getSortedFileTypes();
					for (FileType key: keys) {
						gen.writeNumberField(key.name(), counters.get(key).getCount());
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
	 * @return a sorted list of file types of counters
	 */
	private ArrayList<FileType> getSortedFileTypes() {
		ArrayList<FileType> keys = new ArrayList<>(counters.keySet());
		keys.sort(new Comparator<FileType>() {
			@Override
			public int compare(FileType o1, FileType o2) {
				return o1.ordinal() - o2.ordinal();
			}
		});
		return keys;
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
	
	public void processFile(Repository repo, String path, FileType t, ObjectId obj, int lastModified) throws IOException {
		PrintStream err = System.err;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream s = new PrintStream(buffer);
		System.setErr(s);
		try {
			gen.writeObjectFieldStart(path);
			gen.writeStringField("ObjectId", obj.name());
			gen.writeStringField("LastModified", epochToISO(lastModified));
			gen.writeStringField("FileType", t.name());

			// This may throw MissingObjectException
			ObjectLoader reader = repo.newObjectReader().open(obj); 
			CommentReader comments = null;
			if (reader.isLarge()) {
				comments = FileType.createCommentReader(t, reader.openStream());
			} else {
				byte[] content = reader.getCachedBytes();
				comments = FileType.createCommentReader(t, content);
			}
			
			if (comments != null) {
				counters.computeIfAbsent(t, type -> new Counter()).increment();
				int commentCount = 0;
				while (comments.next()) {
					gen.writeObjectFieldStart(Integer.toString(commentCount++));
					gen.writeObjectField("Text", comments.getText());
					gen.writeObjectField("Line", comments.getLine());
					gen.writeObjectField("CharPositionInLine", comments.getCharPositionInLine());
					gen.writeEndObject();
				}
				gen.writeNumberField("CommentCount", commentCount);
			} else {
				gen.writeStringField("Error", "CommentReadFail");
				gen.writeNumberField("CommentCount", 0);
			}
		} catch (MissingObjectException e) {
			gen.writeStringField("Error", "MissingObjectException");
			gen.writeNumberField("CommentCount", 0);
		} finally {
			s.close();
			if (buffer.size() > 0) {
				gen.writeStringField("Errorlog", buffer.toString());
			}
			gen.writeEndObject();
			System.setErr(err);
		}
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
