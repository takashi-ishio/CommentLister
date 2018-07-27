package jp.naist.se.commentlister;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;



public class GitDiffAnalyzer implements AutoCloseable {

	/**
	 * Extract comments from Git directories.
	 * @param args specify directories.
	 */
	public static void main(String[] args) { 
		if (args.length != 2) {
			System.err.println("Usage: path/to/.git lang");
			return;
		}
		try (GitDiffAnalyzer analyzer = new GitDiffAnalyzer(args[1])) {
			File dir = new File(args[0]).getCanonicalFile();
			String target = "HEAD";
			File gitDir = GitAnalyzer.ensureGitDir(dir);
			if (gitDir != null) {
				analyzer.parseGitRepository(gitDir, target);
			}
		} catch (IOException e) {
			 e.printStackTrace();
		}
	}

	private JsonGenerator gen;
	private FileType targetLanguage;

	public GitDiffAnalyzer(String lang) throws IOException {
		this.targetLanguage = FileType.valueOf(lang.toUpperCase());
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
	 * @param gitDir is a .git directory.
	 * @param target is a revision.
	 */
	public void parseGitRepository(File gitDir, String target) {
		File dir = GitAnalyzer.ensureGitDir(gitDir);
		if (dir == null) return;

		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			gen.writeStartObject();
			AnyObjectId lastCommitId = repo.resolve(target);
			if (lastCommitId != null) {
				try (Git git = new Git(repo)) {
					try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
						diff.setRepository(repo);
						diff.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(SupportedAlgorithm.HISTOGRAM));
						diff.setDiffComparator(RawTextComparator.DEFAULT);
						diff.setDetectRenames(true);
						try {
							Iterable<RevCommit> commits = git.log().add(lastCommitId).call(); 

							// For each commit in the history 
							for (RevCommit commit: commits) {
								if (commit.getParentCount() > 0) {
									
									gen.writeObjectFieldStart(commit.getId().name());
									gen.writeStringField("ShortMessage", commit.getShortMessage());
									gen.writeStringField("CommitTime", epochToISO(commit.getCommitTime()));
									List<DiffEntry> entries = diff.scan(commit.getParent(0).getTree(), commit.getTree());
									// For each modified file
									for (DiffEntry entry: entries) {
										switch (entry.getChangeType()) {
										case ADD:
										{
											FileType t = FileType.getFileType(entry.getNewPath());
											if (isTargetLanguage(t)) {
												analyzeAdd(entry.getNewPath(), repo, t, entry.getNewId());
											}
											break;
										}
										case DELETE:
										{
											FileType t = FileType.getFileType(entry.getOldPath());
											if (isTargetLanguage(t)) {
												analyzeDelete(entry.getOldPath(), repo, t, entry.getOldId());
											}
											break;
										}											
										case COPY:
										{
											FileType t = FileType.getFileType(entry.getNewPath());
											if (isTargetLanguage(t)) {
												analyzeAdd(entry.getNewPath(), repo, t, entry.getOldId());
											}
											break;
										}	
										case MODIFY:
										{
											FileType t = FileType.getFileType(entry.getNewPath());
											if (isTargetLanguage(t)) {
												FileHeader h = diff.toFileHeader(entry);
												analyzeModify(entry.getNewPath(), repo, t, entry.getOldId(), entry.getNewId(), h.toEditList());
											}
											break;
										}	
										case RENAME: // Rename and modify
											FileType t = FileType.getFileType(entry.getNewPath());
											FileType told = FileType.getFileType(entry.getOldPath());
											if (isTargetLanguage(t)) {
												if (told == t) {
													FileHeader h = diff.toFileHeader(entry);
													analyzeModify(entry.getNewPath(), repo, t, entry.getOldId(), entry.getNewId(), h.toEditList());
												} else {
													if (isTargetLanguage(told)) {
														// Delete an language file and add a new file
														analyzeDelete(entry.getOldPath(), repo, told, entry.getOldId());
														analyzeAdd(entry.getNewPath(), repo, t, entry.getNewId());
													} else {
														analyzeAdd(entry.getNewPath(), repo, t, entry.getNewId());
													}
												}
											} else {
												if (isTargetLanguage(told)) {
													// Delete an language file and add a new file
													analyzeDelete(entry.getOldPath(), repo, told, entry.getOldId());
												}												
											}
											break;
										}
									}
									gen.writeEndObject();
								}
							}
						} catch (NoHeadException e) {
							System.err.println("Error: HEAD does not exist.");
						} catch (GitAPIException e) {
							e.printStackTrace();
						}
					}
				}
			} else {
				System.err.println("Error: " + target + " is not a commit ID.");
			}
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean isTargetLanguage(FileType t) {
		return FileType.isSupported(t) && targetLanguage == t;
	}
	
	
//	private static class ErrorRecorder { 
//
//		private PrintStream err;
//		private ByteArrayOutputStream buffer;
//		private PrintStream stream;
//		
//		public ErrorRecorder() {
//			err = System.err;
//			buffer = new ByteArrayOutputStream();
//			stream = new PrintStream(buffer);
//			System.setErr(stream);
//		}
//		
//		public String close() {
//			stream.close();
//			String ret = null;
//			if (buffer.size() > 0) {
//				ret = buffer.toString();
//			}
//			System.setErr(err);
//			return ret;
//		}
//	}
	
	private void analyzeAdd(String pathName, Repository repo, FileType t, AbbreviatedObjectId id) throws IOException {
		analyzeFile(pathName, repo, t, id, "ADDED");
	}
	
	private void analyzeDelete(String pathName, Repository repo, FileType t, AbbreviatedObjectId id) throws IOException {
		analyzeFile(pathName, repo, t, id, "DELETED");
	}
	
	private void analyzeFile(String pathName, Repository repo, FileType t, AbbreviatedObjectId id, String type) throws IOException {
		List<URLInComment> urls = readURLsInComment(repo, t, id);
		if (urls.size() == 0) return; 
		int commentCount = 0;
		gen.writeObjectFieldStart(pathName);
		gen.writeStringField("FileEditType", type);
		for (URLInComment url: urls) {
			gen.writeObjectFieldStart(Integer.toString(commentCount++));
			gen.writeStringField("Type", type);
			gen.writeObjectField("URL", url.getURL());
			gen.writeObjectField("Line", url.getLine());
			gen.writeEndObject();
		}
		gen.writeEndObject();
	}
	
	private static class URLInComment {

		private String url;
		private int line;
		
		public URLInComment(String url, int line) {
			this.url = url;
			this.line = line;
		}
		
		public int getLine() {
			return line;
		}
		
		public String getURL() {
			return url;
		}
	}
	
	private int getRelativeLinePos(String text, int index) {
		int line = 0;
		int endLineIndex = text.indexOf('\n');
		while (endLineIndex >= 0 && endLineIndex < index) {
			line++;
			endLineIndex = text.indexOf('\n', endLineIndex+1);
		}
		return line;	}
	
	private List<URLInComment> readURLsInComment(Repository repo, FileType t, AbbreviatedObjectId id) {
		ArrayList<URLInComment> urls = new ArrayList<>();
		try {
			// This may throw MissingObjectException
			ObjectLoader reader = repo.newObjectReader().open(id.toObjectId()); 
			CommentReader comments = null;
			if (reader.isLarge()) {
				comments = FileType.createCommentReader(t, reader.openStream());
			} else {
				byte[] content = reader.getCachedBytes();
				if (!(new String(content).contains("http"))) return urls;
				comments = FileType.createCommentReader(t, content);
			}
				
			if (comments != null) {
				while (comments.next()) {
					String text = comments.getText();
					int httpindex = text.indexOf("http");
					
					while (httpindex >= 0) {
						int endLineIndex = text.indexOf('\n', httpindex);
						if (endLineIndex < 0) endLineIndex = text.length();
						
						String line = text.substring(httpindex, endLineIndex);
						if (line.endsWith("\r")) line = line.substring(0, line.length()-1);
						int index = line.indexOf(' ');
						if (index > 0) line = line.substring(0, index);
						index = line.indexOf('\t');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf(',');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf(')');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf('(');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf('"');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf('>');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf('\'');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf('}');
						if (index > 0) line = line.substring(0, index);
						index = line.lastIndexOf(']');
						if (index > 0) line = line.substring(0, index);
						if (line.endsWith(".")) line = line.substring(0, line.length()-1);
						if (line.endsWith("\\")) line = line.substring(0, line.length()-1);
						urls.add(new URLInComment(line, comments.getLine() + getRelativeLinePos(text, httpindex)));
						
						httpindex = text.indexOf("http", endLineIndex+1);
					}
				}
			}
		} catch (MissingObjectException e) {
		} catch (IOException e) {
		}
		return urls;
	}
	
	private void analyzeModify(String pathName, Repository repo, FileType t, AbbreviatedObjectId oldVersion, AbbreviatedObjectId newVersion, EditList editlist) throws IOException {
		List<URLInComment> oldURLs = readURLsInComment(repo, t, oldVersion);
		List<URLInComment> newURLs = readURLsInComment(repo, t, newVersion);
		if (oldURLs.size() == 0 && newURLs.size() == 0) return;

		int commentCount = 0;
		gen.writeObjectFieldStart(pathName);
		gen.writeStringField("FileEditType", "MODIFIED");
		if (oldURLs.size() == 0) {
			for (URLInComment url: newURLs) {
				gen.writeObjectFieldStart(Integer.toString(commentCount++));
				gen.writeStringField("Type", "ADDED");
				gen.writeObjectField("NewURL", url.getURL());
				gen.writeObjectField("NewLine", url.getLine());
				gen.writeEndObject();
			}
		} else if (newURLs.size() == 0) {
			for (URLInComment url: oldURLs) {
				gen.writeObjectFieldStart(Integer.toString(commentCount++));
				gen.writeStringField("Type", "DELETED");
				gen.writeObjectField("OldURL", url.getURL());
				gen.writeObjectField("OldLine", url.getLine());
				gen.writeEndObject();
			}
		} else {
			for (URLInComment old: oldURLs) {
				Edit e = findRemoveOrReplace(editlist, old.getLine());
				if (e != null) {
					if (e.getType() == Type.DELETE) {
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "DELETED");
						gen.writeObjectField("OldURL", old.getURL());
						gen.writeObjectField("OldLine", old.getLine());
						gen.writeEndObject();
					} else if (e.getType() == Type.REPLACE) {
						List<URLInComment> insertedURLs = findURLs(newURLs, e.getBeginB(), e.getEndB());
						boolean found = false;
						for (URLInComment u: insertedURLs) {
							if (u.getURL().equals(old.getURL())) {
								found = true;
								// "UNCHANGED" was used for debugging
								//gen.writeObjectFieldStart(Integer.toString(commentCount++));
								//gen.writeStringField("Type", "UNCHANGED");
								//gen.writeObjectField("OldURL", old.getURL());
								//gen.writeObjectField("OldLine", old.getLine());
								//gen.writeObjectField("NewLine", u.getLine());
								//gen.writeEndObject();
								break;
							}
						}
						if (!found) {
							gen.writeObjectFieldStart(Integer.toString(commentCount++));
							if (insertedURLs.size() == 1) {
								gen.writeStringField("Type", "REPLACED");
								gen.writeObjectField("OldURL", old.getURL());
								gen.writeObjectField("OldLine", old.getLine());
								URLInComment url = insertedURLs.get(0);
								gen.writeObjectField("NewURL", url.getURL());
								gen.writeObjectField("NewLine", url.getLine());
							} else if (insertedURLs.size() == 0) {
								gen.writeStringField("Type", "DELETED");
								gen.writeObjectField("OldURL", old.getURL());
								gen.writeObjectField("OldLine", old.getLine());
							} else {
								gen.writeStringField("Type", "REPLACED+ADDED");
								gen.writeObjectField("OldURL", old.getURL());
								gen.writeObjectField("OldLine", old.getLine());
								gen.writeObjectField("NewURLCount", insertedURLs.size());
								for (int i=0; i<insertedURLs.size(); i++) {
									URLInComment url = insertedURLs.get(i);
									gen.writeObjectField("NewURL" + (i+1), url.getURL());
									gen.writeObjectField("NewLine" + (i+1), url.getLine());
								}
							}
							gen.writeEndObject();
						}
						
					}
				} // otherwise, the URL is not affected by a change
			}
			for (URLInComment newURL: newURLs) {
				Edit e = findInsertOrReplace(editlist, newURL.getLine());
				if (e != null) {
					if (e.getType() == Type.INSERT) {
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "ADDED");
						gen.writeObjectField("NewURL", newURL.getURL());
						gen.writeObjectField("NewLine", newURL.getLine());
						gen.writeEndObject();
					} else if (e.getType() == Type.REPLACE) {
						List<URLInComment> deletedURLs = findURLs(oldURLs, e.getBeginA(), e.getEndA());
						if (deletedURLs.size() == 0) {
							// Newly added
							gen.writeObjectFieldStart(Integer.toString(commentCount++));
							gen.writeStringField("Type", "ADDED");
							gen.writeObjectField("NewURL", newURL.getURL());
							gen.writeObjectField("NewLine", newURL.getLine());
							gen.writeEndObject();
						} // otherwise, the newURL has been processed by the loop for oldURLs 
					}					
				}
			}
		}
		
		gen.writeEndObject();
	}
	
	private Edit findRemoveOrReplace(EditList editlist, int line) {
		for (Edit e: editlist) {
			if (e.getType() == Type.INSERT) continue;
			if (e.getBeginA()+1 <= line && line < e.getEndA()+1) {
				return e;
			} else if (line >= e.getEndA()+1) {
				break;
			}
		}
		return null;
	}

	private Edit findInsertOrReplace(EditList editlist, int line) {
		for (Edit e: editlist) {
			if (e.getType() == Type.DELETE) continue;
			if (e.getBeginB()+1 <= line && line < e.getEndB()+1) {
				return e;
			} else if (line >= e.getEndB()+1) {
				break;
			}
		}
		return null;
	}

	private List<URLInComment> findURLs(List<URLInComment> urls, int start, int end) {
		// Adjust DiffEntry indices to line numbers
		start += 1; 
		end += 1;
		
		// Search
		ArrayList<URLInComment> filtered = new ArrayList<>();
		for (URLInComment u: urls) {
			if (start <= u.getLine() && u.getLine() < end) {
				filtered.add(u);
			} else if (u.getLine() >= end) {
				break;
			}
		}
		return filtered;
	}
	
	/**
	 * Translate epoch seconds (Git Commit Time) into an ISO-style string
	 * @param epoch
	 * @return
	 */
	private static String epochToISO(int epoch) {
		return Instant.ofEpochSecond(epoch).toString();		
	}

}
