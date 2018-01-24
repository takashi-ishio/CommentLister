# EXPREF: EXPlicit REFerence Extraction Project

This tool automatically extracts comment in source code.
The tool takes as arguments git repositories, for example:

        java -jar expref-jar-with-dependencies.jar myapp/.git
        java -jar expref-jar-with-dependencies.jar myapp/.git linux.git

The tool reports comments in a JSON format like this:

        {
          "REPO-NAME" : {
            "HEAD" : {
              "FILE-NAME" : {
                "0" : {
                  "Text" : "/* First comment */",
                  "Line" : 2,
                  "CharPositionInLine" : 0
                }
                "1" : {
                  "Text" : "/* Another comment */",
                  "Line" : 4,
                  "CharPositionInLine" : 0
                }
              }
            }
          }
        }

