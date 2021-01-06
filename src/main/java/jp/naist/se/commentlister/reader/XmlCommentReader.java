package jp.naist.se.commentlister.reader;

import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * This reader extracts comments in an XML file
 */
public class XmlCommentReader implements CommentReader {

	private ArrayList<String> text;
	private ArrayList<Location> locations;
	private int index = -1;

	public XmlCommentReader(InputStream stream) {
		text = new ArrayList<>();
		locations = new ArrayList<>();
		try {
			XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(stream); 
			while (reader.hasNext()) {
				int type = reader.next();
				if (type == XMLStreamReader.COMMENT) {
					text.add(reader.getText());
					locations.add(reader.getLocation());
				}
			}
			reader.close();
		} catch (XMLStreamException e) {
		}
		assert text.size() == locations.size();
	}

	public boolean next() {
		index++;
		return index < text.size();
	}
	
	public String getText() {
		return text.get(index);
	}
	
	public int getLine() {
		return locations.get(index).getLineNumber();
	}
	
	/**
	 * This position points to the end of the comment
	 * due to the limitation of XML Reader
	 */
	public int getCharPositionInLine() {
		return locations.get(index).getColumnNumber();
	}
}
