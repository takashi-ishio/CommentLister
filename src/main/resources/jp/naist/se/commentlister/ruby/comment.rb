
require 'ripper'
require 'java'

class CommentScanner < Ripper::Filter
	
	def initialize(src)
		super(src)
		@comments = Array.new  # [text, startline, endline, column]
		@blockcomment = nil
	end
	
	def on_comment(tok, f)
#		puts "#{tok} #{lineno} #{column}"
		if (@comments.length > 0) and (@comments[-1][2] == lineno-1) and (@comments[-1][3] == column)
			@comments[-1][0] = @comments[-1][0] + tok
			@comments[-1][2] = lineno
		else
			@comments << [tok, lineno, lineno, column]
		end
	end
	
	def on_embdoc_beg(tok, f)
		@blockcomment = [tok, lineno, lineno, column]
	end
	
	def on_embdoc(tok, f)
		if @blockcomment
			@blockcomment[0] = @blockcomment[0] + tok
			@blockcomment[2] = lineno
		else # if embdoc is found without embdoc_beg (for safety)
			on_comment(tok, f)
		end
	end
	
	def on_embdoc_end(tok, f)
		if @blockcomment
			@blockcomment[0] = @blockcomment[0] + tok
			@blockcomment[2] = lineno
			@comments << @blockcomment
			@blockcomment = nil
		end
	end
	
	def comments
		@comments
	end
end

def parse(src)
	scanner = CommentScanner.new(src)
	scanner.parse('')
	scanner.comments.map { |c|
		Java::jp.naist.se.commentlister.ruby.RubyCommentReader::Comment.new(c[0], c[1], c[3])
	}
end

parse(value)
