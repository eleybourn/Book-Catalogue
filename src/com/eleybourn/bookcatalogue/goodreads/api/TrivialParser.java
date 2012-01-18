package com.eleybourn.bookcatalogue.goodreads.api;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * DEBUG ONLY. WORK IN PROGRESS.
 * 
 * Parser Handler to collect the raw HTML response. It just reads the output and stores it for later examination.
 * 
 * @author Grunthos
 */
public class TrivialParser extends DefaultHandler {

	private StringBuilder m_Builder = new StringBuilder();

	public TrivialParser() {
	}

	public String getHtml() {
		return m_Builder.toString();
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		m_Builder.append(ch, start, length);
	}

	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);

		//for (int i = 0; i < m_depth; i++)
		//	m_Builder.append(" ");

		m_Builder.append("<");
		if (uri != null && !uri.equals(""))
			m_Builder.append(uri + ":");
		if (localName != null && !localName.equals(""))
			m_Builder.append(localName);

		//if (name != null && !name.equals("")) {
		//	m_Builder.append(" ");
		//	m_Builder.append(name);
		//}
		if (attributes != null && attributes.getLength() > 0) {
			for(int i = 0; i < attributes.getLength(); i++) {
				m_Builder.append(" ");
				String attrName = attributes.getQName(i);
				if (attrName == null || attrName.equals(""))
					attrName = attributes.getLocalName(i);
				m_Builder.append(attrName);
				m_Builder.append("='");
				m_Builder.append(attributes.getValue(i));
				m_Builder.append("'");
			}
		}
		m_Builder.append(">");
	}

	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);

		//for (int i = 0; i < m_depth; i++)
		//	m_Builder.append(" ");

		m_Builder.append("</");
		if (uri != null && !uri.equals(""))
			m_Builder.append(uri + ":");
		if (localName != null && !localName.equals(""))
			m_Builder.append(localName);

		if (name != null && !name.equals("")) {
			m_Builder.append(" ");
			m_Builder.append(name);
		}
		m_Builder.append(">");
	}

}
