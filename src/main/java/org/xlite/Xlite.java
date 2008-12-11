package org.xlite;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.xlite.converters.*;


/**
 * User: peter
 * Date: Feb 25, 2008
 * Time: 11:48:02 AM
 */
public class Xlite {

    RootMapper rootElementMapper;
    private List<ElementConverter> elementConverters;
    private List<ValueConverter> valueConverters;
    private MappingContext mappingContext;


    private boolean initialized = false;
    private Class rootClass;
    private String rootElementName;

    private boolean isStoringUnknownElements;
    private int cacheSize = 1000000;   // default cache size for storing unknown nodes

    private String rootElementNS = XMLConstants.NULL_NS_URI;
    private boolean isPrettyPrint = true;

    public Xlite(Class rootClass, String nodeName) {
        this(rootClass, nodeName, null);
    }

    public Xlite(Class rootClass, String nodeName, String namespaceURI) {
        setupValueConverters();
        setupElementConverters();
        this.rootClass = rootClass;
        this.rootElementName = nodeName;
        this.rootElementNS = namespaceURI;
        this.mappingContext = new MappingContext(elementConverters, valueConverters, rootClass);
    }

    public void setPrettyPrint(boolean prettyPrint){
        this.isPrettyPrint = prettyPrint;
    }

    public void setStoringUnknownElements(boolean storing){
        isStoringUnknownElements = storing;
    }

    public void setCacheSize(int sizeBytes){
          cacheSize = sizeBytes;
    }

    private void initialize() {

        // initialize storing unknown nodes
        if (isStoringUnknownElements) {
            mappingContext.setElementStore(new SubTreeStore(cacheSize));
        } else {
            mappingContext.setElementStore(null);
        }

        // one-time initialization
        if (!initialized) {

            // split xml node name into prefix and local part
            int index = rootElementName.indexOf(':');
            String rootElementLocalpart;
            String rootElementPrefix;
            if (index > 0) {  // with prefix ("prefix:localpart")
                rootElementPrefix = rootElementName.substring(0, index);
                rootElementLocalpart = rootElementName.substring(index + 1, rootElementName.length());

            } else if (index == 0) { // empty prefix (no prefix defined - e.g ":nodeName")
                rootElementPrefix = XMLConstants.DEFAULT_NS_PREFIX;
                rootElementLocalpart = rootElementName.substring(1, rootElementName.length());

            } else { // no prefix given
                rootElementPrefix = XMLConstants.DEFAULT_NS_PREFIX;
                rootElementLocalpart = rootElementName;
            }

            // namespace  of root element is not defined
            if (rootElementNS == null) {
                rootElementNS = mappingContext.getPredefinedNamespaces().getNamespaceURI(rootElementPrefix);
            }
            this.rootElementMapper = new RootMapper(new QName(rootElementNS, rootElementLocalpart, rootElementPrefix), rootClass, mappingContext);
            initialized = true;
        }
    }

    private void setupElementConverters() {
        elementConverters = new ArrayList<ElementConverter>();
        elementConverters.add(new CollectionConverter());
        elementConverters.add(new ElementHolderConverter());

        // wraps every ValueConverter so that it can be used as a ElementConverter
        for (ValueConverter valueConverter : valueConverters) {
            elementConverters.add(new ValueConverterWrapper(valueConverter));
        }
    }

    private void setupValueConverters() {
        valueConverters = new ArrayList<ValueConverter>();

        valueConverters.add(new StringConverter());
        valueConverters.add(new IntConverter());
        valueConverters.add(new DoubleConverter());
        valueConverters.add(new FloatConverter());
        valueConverters.add(new LongConverter());
        valueConverters.add(new ShortConverter());
        valueConverters.add(new BooleanConverter());
        valueConverters.add(new ByteConverter());
        valueConverters.add(new CharConverter());

    }

    public void addNamespace(String namespace) {
        mappingContext.addNamespace(namespace);
    }


    public Object fromXML(Reader reader) {
        initialize();

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader xmlreader = null;
        try {
            xmlreader = factory.createXMLStreamReader(reader);
        } catch (XMLStreamException e) {
            throw new XliteException("Error initalizing XMLStreamReader", e);
        }
        XMLSimpleReader simpleReader = new XMLSimpleReader(xmlreader, isStoringUnknownElements);

        return rootElementMapper.getRootObject(simpleReader);
    }

    public void toXML(Object source, Writer writer) {
        initialize();

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        factory.setProperty("javax.xml.stream.isRepairingNamespaces", true);
        XMLStreamWriter parser = null;
        try {
            parser = factory.createXMLStreamWriter(writer);
        } catch (XMLStreamException e) {
            throw new XliteException("Error initalizing XMLStreamWriter", e);
        }
        XMLSimpleWriter simpleWriter = new XMLSimpleWriter(parser, new XmlStreamSettings(), isPrettyPrint);

        rootElementMapper.toXML(source, simpleWriter);

    }

    public SubTreeStore getNodeStore() {
        return mappingContext.getElementStore();
    }
}
