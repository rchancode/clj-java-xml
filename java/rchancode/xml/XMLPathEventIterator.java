package rchancode.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class XMLPathEventIterator implements Iterator<XMLPathEvent> {

    private XMLEventReader xmlEventReader;
    private List<String> path = new ArrayList<String>();


    public XMLPathEventIterator(XMLEventReader xmlEventReader) {
        this.xmlEventReader = xmlEventReader;
    }

    public boolean hasNext() {
        return xmlEventReader.hasNext();
    }

    public XMLPathEvent next() {
        try {
            XMLEvent evt = xmlEventReader.nextEvent();
            if (evt.isStartElement()) {
                StartElement element = evt.asStartElement();
                QName qname = element.getName();
                String name;
                if (!qname.getNamespaceURI().isEmpty()) {
                    name = "{" + qname.getNamespaceURI() + "}" + qname.getLocalPart();
                } else {
                    name = qname.getLocalPart();
                }
                path.add(name);
            }
            XMLPathEvent result = new XMLPathEvent(getPathString(), evt);
            // The end element is considered in the same path, hence only remove for next return but return
            // previous path.
            if (evt.isEndElement()) {
                path.remove(path.size() - 1);
            }
            return result;
        } catch (XMLStreamException e1) {
            return null;
        }
    }

    public void remove() {
    }

    private String toPathStr(List<String> path) {
        if (path.isEmpty()) {
            return "/";
        } else {
            StringBuilder result = new StringBuilder("");
            for (String item : path) {
                result.append("/");
                result.append(item);
            }
            return result.toString();
        }
    }

    private String getPathString() {
        return toPathStr(path);
    }

}
