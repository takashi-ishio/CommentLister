package jp.naist.se.commentlister;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.commentlister.reader.CommentReader;
import jp.naist.se.commentlister.reader.FileType;



public class GitDiffAnalyzer implements AutoCloseable {

	/**
	 * Extract modified comments including "http" from a Git repository.
	 * @param args specify a directory, a programming language, and a list of commits (one commit id per line) to be analyzed.
	 */
	public static void main(String[] args) { 
		if (args.length != 3) {
			System.err.println("Usage: path/to/.git lang COMMIT-LIST-FILE");
			return;
		}
		long t = System.currentTimeMillis();
		try (GitDiffAnalyzer analyzer = new GitDiffAnalyzer(args[1])) {
			File dir = new File(args[0]).getCanonicalFile();
			ArrayList<String> targets = readTargetList(args[2]);
			
			File gitDir = GitAnalyzer.ensureGitDir(dir);
			if (gitDir != null) {
				analyzer.parseGitRepository(gitDir, targets);
			}
		} catch (IOException e) {
			 e.printStackTrace();
		}
		System.err.println(args[0] + "," + (System.currentTimeMillis() - t));
	}
	
	public static ArrayList<String> readTargetList(String filename) {
		try (LineNumberReader reader = new LineNumberReader(new FileReader(new File(filename)))) {
			ArrayList<String> list = new ArrayList<>(65536);
			for (String line=reader.readLine(); line !=null; line = reader.readLine()) {
				list.add(line);
			}
			return list;
		} catch (IOException e) {
			return new ArrayList<>(0);
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
	public void parseGitRepository(File gitDir, ArrayList<String> targets) {
		File dir = GitAnalyzer.ensureGitDir(gitDir);
		if (dir == null) return;

		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			gen.writeStartObject();
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (DiffFormatter diff = new DiffFormatter(out)) {
				diff.setRepository(repo);
				diff.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(SupportedAlgorithm.HISTOGRAM));
				diff.setDiffComparator(RawTextComparator.DEFAULT);
				diff.setDetectRenames(true);
				
				for (String target: targets) {

					AnyObjectId commitId = repo.resolve(target);
					if (commitId != null) {
						RevCommit commit = repo.parseCommit(commitId);
						
						RevCommit parent = null;
						if (commit.getParentCount() > 0) {
							parent = commit.getParent(0);
						}

						gen.writeObjectFieldStart(commit.getId().name());
						gen.writeStringField("ShortMessage", commit.getShortMessage());
						gen.writeStringField("CommitTime", epochToISO(commit.getCommitTime()));
						
						List<DiffEntry> entries = diff.scan(parent, commit);
							
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
									out.reset();
									diff.format(entry);
									boolean inclusion = out.toString().contains("http");
									if (!inclusion) {
										continue;
									}

									FileHeader h = diff.toFileHeader(entry);
									analyzeModify(entry.getNewPath(), repo, t, entry.getOldId(), entry.getNewId(), h.toEditList());
								}
								break;
							}	
							case RENAME: // Rename and modify
								FileType t = FileType.getFileType(entry.getNewPath());
								FileType told = FileType.getFileType(entry.getOldPath());
								if (isTargetLanguage(t)) {
									out.reset();
									diff.format(entry);
									boolean inclusion = out.toString().contains("http");
									if (!inclusion) {
										continue;
									}
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
					} else {
						System.err.println("Error: " + target + " is not a commit ID.");
					}
				}
			}
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean isTargetLanguage(FileType t) {
		return FileType.isSupported(t) && targetLanguage == t;
	}
	
	
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
			gen.writeObjectField("CommentLine", url.getCommentStartLine());
			gen.writeEndObject();
		}
		gen.writeEndObject();
	}
	
	private static class URLInComment {

		private String url;
		private int line;
		private int commentStartLine;
		
		public URLInComment(String url, int line, int commentStart) {
			this.url = url;
			this.line = line;
			this.commentStartLine = commentStart; 
		}
		
		public int getLine() {
			return line;
		}
		
		public String getURL() {
			return url;
		}
		
		public int getCommentStartLine() {
			return commentStartLine;
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
						
						// Remove special symbols from the end of a http link.
						// This code comes from a script that extracts http links from GitAnalyzer result. 
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
						urls.add(new URLInComment(line, comments.getLine() + getRelativeLinePos(text, httpindex), comments.getLine()));
						
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
		int oldIndex = 0;
		int newIndex = 0;
		for (Edit e: editlist) {
			if (newIndex >= newURLs.size() && oldIndex >= oldURLs.size()) break;

			if (e.getType() == Type.INSERT) {
				while (newIndex < newURLs.size()) {
					URLInComment url = newURLs.get(newIndex); 
					if (e.getBeginB()+1 <= url.getLine() && url.getLine() < e.getEndB()+1) {
						// Record the URL as ADDED
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "ADDED");
						gen.writeObjectField("NewURL", url.getURL());
						gen.writeObjectField("NewLine", url.getLine());
						gen.writeObjectField("NewCommentLine", url.getCommentStartLine());
						gen.writeEndObject();
					} else if (url.getLine() >= e.getEndB()+1) {
						// The URL should be checked for the next difference
						break;
					}
					newIndex++;
				}
			} else if (e.getType() == Type.DELETE) {
				while (oldIndex < oldURLs.size()) {
					URLInComment url = oldURLs.get(oldIndex); 
					if (e.getBeginA()+1 <= url.getLine() && url.getLine() < e.getEndA()+1) {
						// Record the URL as DELETED
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "DELETED");
						gen.writeObjectField("OldURL", url.getURL());
						gen.writeObjectField("OldLine", url.getLine());
						gen.writeObjectField("OldCommentLine", url.getCommentStartLine());
						gen.writeEndObject();
					} else if (url.getLine() >= e.getEndA()+1) {
						// The URL should be checked for the next difference
						break;
					}
					oldIndex++;
				}
			} else if (e.getType() == Type.REPLACE) {
				// Obtain all DELETED URLs
				ArrayList<URLInComment> deleted = new ArrayList<>();
				while (oldIndex < oldURLs.size()) {
					URLInComment url = oldURLs.get(oldIndex); 
					if (e.getBeginA()+1 <= url.getLine() && url.getLine() < e.getEndA()+1) {
						deleted.add(url);
					} else if (url.getLine() >= e.getEndA()+1) {
						break;
					}
					oldIndex++;
				}
				// Obtain all INSERTED URLs
				ArrayList<URLInComment> added = new ArrayList<>();
				while (newIndex < newURLs.size()) {
					URLInComment url = newURLs.get(newIndex); 
					if (e.getBeginB()+1 <= url.getLine() && url.getLine() < e.getEndB()+1) {
						added.add(url);
					} else if (url.getLine() >= e.getEndB()+1) {
						break;
					}
					newIndex++;
				}
				//
				if (deleted.size() > 0 && added.size() == 0) { 
					for (int i=0; i<deleted.size(); i++) {
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "DELETED");
						gen.writeObjectField("OldURL", deleted.get(i).getURL());
						gen.writeObjectField("OldLine", deleted.get(i).getLine());
						gen.writeObjectField("OldCommentLine", deleted.get(i).getCommentStartLine());
						gen.writeEndObject();
					}
				} else if (deleted.size() == 0 && added.size() > 0) {
					for (int i=0; i<added.size(); i++) {
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "ADDED");
						gen.writeObjectField("NewURL", added.get(i).getURL());
						gen.writeObjectField("NewLine", added.get(i).getLine());
						gen.writeObjectField("NewCommentLine", added.get(i).getCommentStartLine());
						gen.writeEndObject();
					}
				} else if (deleted.size() > 0 && added.size() > 0) {
					// Compare the URLs
					boolean changed = false;
					if (deleted.size() == added.size()) {
						for (int i=0; i<deleted.size(); i++) {
							if (!deleted.get(i).getURL().equals(added.get(i).getURL())) {
								changed = true;
								break;
							}
						}
					} else {
						changed = true;
					}
					if (changed) {
						gen.writeObjectFieldStart(Integer.toString(commentCount++));
						gen.writeStringField("Type", "REPLACED");
						gen.writeObjectField("OldURLCount", deleted.size());
						gen.writeObjectField("NewURLCount", added.size());
						for (int i=0; i<deleted.size(); i++) {
							gen.writeObjectField("OldURL" + (i+1), deleted.get(i).getURL());
							gen.writeObjectField("OldLine" + (i+1), deleted.get(i).getLine());
							gen.writeObjectField("OldCommentLine" + (i+1), deleted.get(i).getCommentStartLine());
						}
						for (int i=0; i<added.size(); i++) {
							gen.writeObjectField("NewURL" + (i+1), added.get(i).getURL());
							gen.writeObjectField("NewLine" + (i+1), added.get(i).getLine());
							gen.writeObjectField("NewCommentLine" + (i+1), added.get(i).getCommentStartLine());
						}
						gen.writeEndObject();
					}
				}
			}
		}
		
		gen.writeEndObject();
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
