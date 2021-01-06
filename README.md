# Comment Lister: Code Comment Listing Tool for Git repository

This tool automatically extracts comment in source code stored in a git repository.
The supported programming langauges are: C/C++14, Java8, ECMAScript, Python3, PHP, Ruby, and C#. 

This tool has been developed for writing the following paper: 
> Hideaki Hata, Christophe Treude, Raula Gaikovina Kula, Takashi Ishio:
> 9.6 million links in source code comments: purpose, evolution, and decay (ICSE 2019)

## Build

The project uses Maven.  
You can make a runnable jar `CommentLister.jar` by executing `mvn package`. 


## Usage of Comment Extraction

The tool takes a file path to a git repository to be analyzed, for example:

        java -jar CommentLister.jar myapp/.git

All code comments in source code are extracted from the HEAD revision of the specified repository.  
An optional argument can choose a particular revision using a tag or a commit ID.

        java -jar CommentLister.jar myapp/.git -target=tag1
        java -jar CommentLister.jar myapp/.git -target=502af45

The tool reports comments in a JSON format.
The entire output is an object including the following attributes:
 - Repository: A specified directory.
 - Revision: A specified revision (HEAD by default).
 - ObjectId: The commit ID of the revision.
 - CommitTime: The commit time of the revision.
 - Files: A set of files (JSON Object). 
   - For each file, its file path, object ID, last modified time, file type, and a list of comments, and the number of comments are recorded.
   - A comment data includes the text, the line number, and char position in the line.
 - FileTypes: The numbers of source files recognized by the tool.
 - ElapsedTime: Milliseconds elapsed to process the files. 

The following JSON is an actual example extracted from the project's git repository.

        {
          "Repository" : "CommentLister/.git",
          "Revision" : "HEAD",
          "ObjectId" : "502af45efc6589972744a4bf90b90f6579e3189d",
          "CommitTime" : "2018-02-27T05:37:54Z",
          "Files" : {
            "src/jp/naist/se/commentlister/FileType.java" : {
              "ObjectId" : "d045a672cbf99df939a4b7d1a6be46120964b065",
              "LastModified" : "2018-02-27T05:37:54Z",
              "FileType" : "JAVA",
              "0" : {
                "Text" : "// Remove directories ",
                "Line" : 52,
                "CharPositionInLine" : 2
              },
              "1" : {
                "Text" : "// Mac OS's backup file",
                "Line" : 56,
                "CharPositionInLine" : 35
              },
              ...
              },
              "CommentCount" : 5
            },
          },
          "FileTypes" : {
            "JAVA" : 3
          },
          "ElapsedTime" : 562
        }



## Usage of Modified URL Extraction 

`GitDiffAnalyzer` extracts modified http(s) links from commits, while the main class of the tool (`jp.naist.se.commentlister.GitAnalyzer`) extracts all comments from a particular revision. 
The class requires three options: 
 - file path to repository, 
 - a programming language (one of CPP, JAVA, ECMASCRIPT, CSHARP, PYTHON, PHP, and RUBY)
 - a list of commits to be analyzed (you can make it by `git log --pretty=format:%H`)

The following commands extract URL changes from a repository in the current directory.

     git log --pretty=format:%H > commitid.txt
     java -classpath CommentLister.jar jp.naist.se.commentlister.GitDiffAnalyzer . java commitid.txt

The class reports added/deleted/modified URLs in a JSON format.
For each commit, comments including URLs are listed. 
An example extracted from <https://github.com/takashi-ishio/CommentLister-Test> repository is following: 

        {
          "41c0d21c53fd9b4e225be2eaa031ad8e13c25f88" : {
            "ShortMessage" : "Commit without URL change",
            "CommitTime" : "2018-08-08T02:26:47Z"
          },
          "2fe221a11d3c485861317e9747d02abec74b807e" : {
            "ShortMessage" : "Replaced URLs",
            "CommitTime" : "2018-07-27T05:22:32Z",
            "src/example/F.java" : {
              "FileEditType" : "MODIFIED",
              "0" : {
                "Type" : "DELETED",
                "OldURL" : "http://github.com/takashi-ishio/3",
                "OldLine" : 11,
                "OldCommentLine" : 10
              },
              "1" : {
                "Type" : "ADDED",
                "NewURL" : "http://github.com/takashi-ishio/3",
                "NewLine" : 12,
                "NewCommentLine" : 10
              }
            }
          }
        }



## Supported Languages

The tool chooses a lexer for a source file using its file extension (case-insensitive).

|Language|File Extensions|Comment Features|
|:-------|:--------------|:---------------|
|C/C++ 14|.c, .cc, .cp, .cpp, .cx, .cxx, .c+, .c++, .h, .hh, .hxx, .h+, .h++, .hp, .hpp|`//`, `/* ... */`|
|Java 8|.java|`//`, `/* ... */`|
|ECMAScript|.js|`//`, `/* ... */`|
|C#|.cs|`//`, `/* ... */`|
|Python 3|.py|`#` and long string literals (""" ... """). The literals include docstrings and regular string literals.|
|PHP|.php|`//`, `/* ... */`, `#`, and HTML comments. It may not preserve white space, because PHP allows `<?php // comment ?>` in a single line.|
|Ruby 2.3|.rb|`#`, `=begin ... =end`|

For developers: The rules are included in `jp.naist.se.commentlister.FileType` class.

Note that single-line comments in consecutive lines are regarded as a single multi-line comment, if the comments have the same char position in the lines. The following snippets are examples.

```Ruby
# 1st line
# 2nd line
```

```c++
/* 1st line */
/* 2nd line */
```

```c++
int x = 0;   // 1st line
             // 2nd line
```


## Performance

Simple execution (not an organized evaluation) on a workstation with Xeon E5-2690 v3 2.60GHz results:
 - 56 seconds for Linux git repository (42,000 files, 2.1 GB)
 - 218 seconds for Intellij-Community git repository (66,000 files, 2.8GB)
 - 1502 seconds for Gecko-dev git repository (90,000 files, 3.8 GB)


## Utilities

The project also contains two utilities.


### FileAnalyzer to directly extract comments from source files

This command takes file names as arguments and extracts comments from the files.

     java -classpath CommentLister.jar jp.naist.se.commentlister.FileAnalyzer [source files]



### GitFileList to count the number of files in a git repo

This command takes a git repo and file patterns.

     java -classpath CommentLister.jar jp.naist.se.commentlister.GitFileCount path/to/.git [-f pattern] [-target=tag/commitId]

`-f pattern` specifies a wild card pattern like "*.java".
The command accepts multiple patterns and reports the number of files for each pattern in the revision.


## Directory Structure

  - `src/main` is the main source directory.
    - `src/main/java` includes main java files.
    - `src/main/antlr4` includes grammar files to generate lexers.  
      - The files come from https://github.com/antlr/grammars-v4/. 
      - Some of them are modified to push comments into HIDDEN channel, since the original ones simply discard the comments.  
    - `src/main/resources` includes a ruby file to parse Ruby source files.     

## Dependencies

 - [JGit](https://www.eclipse.org/jgit/) to process a git repository
 - [ANTLR4](http://www.antlr.org/index.html) to extract comments from source code
 - [Jackson Core](https://github.com/FasterXML/jackson-core) to generate a JSON file
 - [JRuby](http://jruby.org/) to parse Ruby source files
 - [Apache Commons IO](https://commons.apache.org/proper/commons-io/) to translate a stream to a byte array 
