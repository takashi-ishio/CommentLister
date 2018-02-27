# Comment Lister: Code Comment Listing Tool for Git repository

## Build

The project uses Maven.  
You can make a runnable jar `CommentLister.jar` by executing `mvn package`. 


## Usage

This tool automatically extracts comment in source code stored in a git repository.
The supported programming langauges are: C/C++14, Java8, ECMAScript, Python3, and C#. 

The tool takes a file path to a git repository to be analyzed, for example:

        java -jar CommentLister.jar myapp/.git

All code comments in source code are extracted from the HEAD revision of the specified repository.  
An optional argument can choose a particular revision using a tag or a commit ID.

        java -jar CommentLister.jar myapp/.git tag1
        java -jar CommentLister.jar myapp/.git 502af45

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

## Performance

Simple execution (not an organized evaluation) on a workstation with Xeon E5-2690 v3 2.60GHz results:
 - 56 seconds for Linux git repository (around 42000 files)
 - 218 seconds for Intellij-Community git repository (around 66000 files)

## Dependencies

 - [JGit](https://www.eclipse.org/jgit/) to process a git repository,
 - [ANTLR4](http://www.antlr.org/index.html) to extract comments from source code, and
 - [Jackson Core](https://github.com/FasterXML/jackson-core) to generate a JSON file.
