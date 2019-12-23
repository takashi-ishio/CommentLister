lexer grammar MakefileComment;

INSTRUCTION: . -> skip;
COMMENT: '#' (~[\n]*? '\\' '\r'? '\n')+ ~[\n]+ NEWLINE;
NEWLINE: '\r' '\n'? | '\n' | EOF;

