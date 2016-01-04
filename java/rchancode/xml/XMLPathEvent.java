package rchancode.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Writer;

/**
 * Created by rchan on 11/11/15.
 */
public class XMLPathEvent implements XMLEvent {

    private String path;

    public XMLEvent getEvent() {
        return event;
    }

    private XMLEvent event;

    public XMLPathEvent(String path, XMLEvent event) {
        this.path = path;
        this.event = event;
    }

    public String getPath() {
        return path;
    }

    public boolean isEndElement() {
        return event.isEndElement();
    }

    @Override
    public boolean isEntityReference() {
        return event.isEntityReference();
    }

    @Override
    public boolean isProcessingInstruction() {
        return event.isProcessingInstruction();
    }

    @Override
    public int getEventType() {
        return event.getEventType();
    }

    @Override
    public Location getLocation() {
        return event.getLocation();
    }

    public boolean isStartElement() {
        return event.isStartElement();
    }

    @Override
    public boolean isAttribute() {
        return event.isAttribute();
    }

    @Override
    public boolean isNamespace() {
        return event.isNamespace();
    }

    public boolean isCharacters() {
        return event.isCharacters();
    }

    @Override
    public boolean isStartDocument() {
        return event.isStartDocument();
    }

    @Override
    public boolean isEndDocument() {
        return event.isEndDocument();
    }

    @Override
    public StartElement asStartElement() {
        return event.asStartElement();
    }

    @Override
    public EndElement asEndElement() {
        return event.asEndElement();
    }

    @Override
    public Characters asCharacters() {
        return event.asCharacters();
    }

    @Override
    public QName getSchemaType() {
        return event.getSchemaType();
    }

    @Override
    public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
        event.writeAsEncodedUnicode(writer);
    }

}
