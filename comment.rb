
require 'ripper'

class CommentScanner < Ripper::Filter
  def on_comment(tok, f)
    puts "#{tok} #{lineno} #{column}"
  end
  def on_embdoc_beg(tok, f)
    puts "#{tok} #{lineno} #{column}"
  end
  def on_embdoc(tok, f)
    puts "#{tok} #{lineno} #{column}"
  end
  def on_embdoc_end(tok, f)
    puts "#{tok} #{lineno} #{column}"
  end
end

CommentScanner.new(value).parse('')
