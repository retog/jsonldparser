/*
 * The MIT License
 *
 * Copyright 2016 Zazuko GmbH.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.zazuko.jsonld.parser;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParserFactory;
import org.apache.clerezza.commons.rdf.BlankNode;
import org.apache.clerezza.commons.rdf.BlankNodeOrIRI;
import org.apache.clerezza.commons.rdf.Graph;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.commons.rdf.Language;
import org.apache.clerezza.commons.rdf.Literal;
import org.apache.clerezza.commons.rdf.RDFTerm;
import org.apache.clerezza.commons.rdf.Triple;
import org.apache.clerezza.commons.rdf.impl.utils.PlainLiteralImpl;
import org.apache.clerezza.commons.rdf.impl.utils.TripleImpl;
import org.apache.clerezza.commons.rdf.impl.utils.TypedLiteralImpl;
import org.apache.clerezza.rdf.ontologies.RDF;

/**
 *
 * @author user
 */
public class JsonLdParser {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws FileNotFoundException {
        final String fileName = args[0];
        final File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("File " + file + " does not exist");
            System.exit(-1);
        }
        final FileInputStream in = new FileInputStream(file);
        parse(in, System.out);
    }

    static void parse(final InputStream in, final OutputStream out) {
        parse(in, out, null);
    }

    static void parse(final InputStream in, final OutputStream out, final IRI base) {
        final PrintWriter printWriter;
        try {
            printWriter = new PrintWriter(new OutputStreamWriter(out, "utf-8"), true);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
        parse(in, new TripleSink() {

            final WeakHashMap<BlankNode, String> node2IdMap = new WeakHashMap<>();
            int idCounter = 1;

            @Override
            public void add(Triple triple) {
                printWriter.println(toNT(triple));
            }

            private String toNT(Triple triple) {
                return toNT(triple.getSubject()) + " " + toNT(triple.getPredicate()) + " " + toNT(triple.getObject()) + ".";
            }

            private String toNT(Literal literal) {
                //TODO real impl
                return literal.toString();
            }

            private String toNT(IRI iri) {
                //TODO real impl
                return iri.toString();
            }

            private String toNT(BlankNode node) {
                String id = node2IdMap.get(node);
                if (id == null) {
                    id = Integer.toString(idCounter);
                    idCounter++;
                    node2IdMap.put(node, id);
                }
                return "_:" + id;
            }

            private String toNT(RDFTerm node) {
                if (node instanceof Literal) {
                    return toNT((Literal) node);
                } else {
                    return toNT((BlankNodeOrIRI) node);
                }
            }

            private String toNT(BlankNodeOrIRI node) {
                if (node instanceof IRI) {
                    return toNT((IRI) node);
                } else {
                    return toNT((BlankNode) node);
                }
            }
        }, base);
    }

    static void parse(final InputStream in, final Graph graph) {
        parse(in, graph, null);
    }

    static void parse(final InputStream in, final Graph graph, final IRI base) {
        parse(in, new TripleSink() {
            @Override
            public void add(Triple triple) {
                graph.add(triple);
            }

        }, base);
    }

    static void parse(InputStream in, TripleSink sink) {
        parse(in, sink, null);
    }

    static void parse(InputStream in, TripleSink sink, final IRI base) {
        final JsonParserFactory factory = Json.createParserFactory(null);
        final JsonParser jsonParser = factory.createParser(in, Charset.forName("utf-8"));
        JsonLdParser jsonLdParser = new JsonLdParser(jsonParser, sink, base);
        jsonLdParser.parse();
    }

    private final JsonParser jsonParser;
    private final TripleSink sink;
    private final Map<String, BlankNode> label2bnodeMap = new HashMap<>();
    private Context context = new Context();

    private JsonLdParser(JsonParser jsonParser, TripleSink sink, IRI base) {
        this.jsonParser = jsonParser;
        this.sink = sink;
        context.setBaseIRI(base);
    }

    private void parse() {
        final Event firstEvent = jsonParser.next();
        switch (firstEvent) {
            case START_OBJECT: {
                parseJsonObject();
                break;
            }
            default: {
                throw new RuntimeException("Document should start with object: " + firstEvent);
            }
        }
    }

    private void parseJsonObject() {
        JsonObjectParser subjectParser = new JsonObjectParser(true);
        subjectParser.parse();
    }

    private BlankNode getBlankNode(String identifier) {
        BlankNode result = label2bnodeMap.get(identifier);
        if (result == null) {
            result = new BlankNode();
            label2bnodeMap.put(identifier, result);
        }
        return result;
    }

    private BlankNodeOrIRI parseBNodeRelativeUriOrCurie(final String identifier) {
        if (identifier.startsWith("_:")) {
            return getBlankNode(identifier);
        } else {
            return context.resolveRelativeUriOrCurie(identifier);
        }
    }

    private BlankNodeOrIRI parseKeyOrType(final String identifier) {
        if (identifier.startsWith("_:")) {
            return getBlankNode(identifier);
        } else {
            return context.resolveKeyOrType(identifier);
        }
    }

    /**
     * Parses JSon Objects
     */
    class JsonObjectParser {

        private BlankNodeOrIRI ambiguousTypeIRI = null;
        private String value = null;
        private Language language = null;
        private RDFTerm node;
        Context origContext = null;
        private final boolean isRoot;

        public JsonObjectParser() {
            isRoot = false;
        }

        public JsonObjectParser(boolean isRoot) {
            this.isRoot = isRoot;
        }

        public void parse() {
            JsonParser.Event first = jsonParser.next();
            if (!first.equals(JsonParser.Event.KEY_NAME)) {
                throw new RuntimeException("Sorry");
            }
            String firstKey = jsonParser.getString();
            while (firstKey.equals("@id") || firstKey.equals("@context")) {
                if (firstKey.equals("@id")) {
                    node = parseId();
                    if (jsonParser.next().equals(JsonParser.Event.END_OBJECT)) {
                        return;
                    }
                }
                if (firstKey.equals("@context")) {
                    final ContextParser contextParser = new ContextParser();
                    origContext = context;
                    context = contextParser.parse();
                    if (jsonParser.next().equals(JsonParser.Event.END_OBJECT)) {
                        return;
                    }
                }
                firstKey = jsonParser.getString();
            }
            handleKey();
            while (jsonParser.hasNext()) {
                final Event next = jsonParser.next();
                switch (next) {
                    case KEY_NAME: {
                        handleKey();
                        break;
                    }
                    case END_OBJECT: {
                        if (value != null) {
                            if (node != null) {
                                throw new RuntimeException("@value combined with incompatible key");
                            }
                            if (language != null) {
                                node = new PlainLiteralImpl(value, language);
                            } else {
                                node = new TypedLiteralImpl(value, (IRI) ambiguousTypeIRI);
                            }
                            if (origContext != null) {
                                context = origContext;
                            }
                            return;
                        }
                        if (ambiguousTypeIRI != null) {
                            sink.add(new TripleImpl(getSubject(), RDF.type, ambiguousTypeIRI));
                        }
                        return;
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }

            }
        }

        //called when the resource represented by this node is used as subject
        private BlankNodeOrIRI getSubject() {
            if (node == null) {
                node = new BlankNode();
            }
            return (BlankNodeOrIRI) node;
        }

        private BlankNodeOrIRI parseId() {
            jsonParser.next();
            final String identifier = jsonParser.getString();
            return parseBNodeRelativeUriOrCurie(identifier);
        }

        private IRI getIRI(String keyName) {
            return new IRI(keyName);
        }

        private void handleKey() {
            final String keyName = jsonParser.getString();
            if (keyName.equals("@type")) {
                //either datatype or rdf type
                //@type value must a string, an array of strings (, or an empty object?)
                BlankNodeOrIRI[] types;
                final Event next = jsonParser.next();
                switch (next) {
                    case VALUE_STRING: {
                        types = new BlankNodeOrIRI[]{parseBNodeRelativeUriOrCurie(jsonParser.getString())};
                        break;
                    }
                    case START_ARRAY: {
                        types = readTypes();
                        break;
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }
                //if a single type is defined it could also be a datype for a literal
                //otherwise they are rdf types
                if (types.length == 1) {
                    ambiguousTypeIRI = types[0];
                } else {
                    for (BlankNodeOrIRI type : types) {
                        sink.add(new TripleImpl(getSubject(), RDF.type, type));
                    }
                }
                return;
            }
            if (keyName.equals("@language")) {
                final Event next = jsonParser.next();
                switch (next) {
                    case VALUE_STRING: {
                        language = new Language(jsonParser.getString());
                        break;
                    }
                    default: {
                        throw new RuntimeException("Language must be a string" + next);
                    }
                }
                return;
            }
            if (keyName.equals("@value")) {
                final Event next = jsonParser.next();
                switch (next) {
                    case VALUE_STRING: {
                        value = jsonParser.getString();
                        break;
                    }
                    default: {
                        throw new RuntimeException("Value must be a string" + next);
                    }
                }
                return;
            }
            if (keyName.equals("@graph")) {
                parseGraph();
                return;
            }
            if (keyName.equals("@list")) {
                if (node != null) {
                    throw new RuntimeException("@list not expectd here");
                }
                parseList();
                return;
            }
            final BlankNodeOrIRI property = parseKeyOrType(keyName);
            if (!(property instanceof IRI)) {
                throw new RuntimeException("Sorry Clerezza only handles RDF, so BlankNodes in predicate poistion are not supported");
            }
            final ObjectParser subjectPredicateParser = new ObjectParser(getSubject(), (IRI) property);
            subjectPredicateParser.parse();

        }

        private IRI[] readTypes() {
            final List<IRI> types = new VirtualFlow.ArrayLinkedList<>();
            while (jsonParser.hasNext()) {
                final Event next = jsonParser.next();
                switch (next) {
                    case VALUE_STRING: {
                        types.add(new IRI(jsonParser.getString()));
                        break;
                    }
                    case END_ARRAY: {
                        return types.toArray(new IRI[types.size()]);
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }
            }
            throw new RuntimeException("Unterminated Array");
        }

        private void parseList() {
            final Event nextEvent = jsonParser.next();
            switch (nextEvent) {
                case START_ARRAY: {
                    node = parseListRest();
                    break;
                }
                default:
                    throw new RuntimeException("Expected start of array, got: " + nextEvent);
            }
        }

        private BlankNodeOrIRI parseListRest() {
            final Event nextEvent = jsonParser.next();
            switch (nextEvent) {
                case END_ARRAY: {
                    return new IRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil");
                }
                default: {
                    BlankNode listNode = new BlankNode();
                    final ObjectParser subjectPredicateParser = new ObjectParser(listNode,
                            new IRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"));
                    subjectPredicateParser.parse(nextEvent);
                    sink.add(new TripleImpl(listNode,
                            new IRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest"), parseListRest()));
                    return listNode;
                }
            }
        }

        private void parseGraph() {
            if (!isRoot) {
                throw new RuntimeException("Currently @graph is only supported in the root object");
            }
            final Event nextEvent = jsonParser.next();
            switch (nextEvent) {
                case START_ARRAY: {
                    parseArray();
                    break;
                }
                case START_OBJECT: {
                    throw new RuntimeException("node object as value of @graph not supported yet.");
                }
                default:
                    throw new RuntimeException("Expected start of array, got: " + nextEvent);
            }

        }

        private void parseArray() {
            while (jsonParser.hasNext()) {
                final Event next = jsonParser.next();
                switch (next) {
                    case START_OBJECT: {
                        final JsonObjectParser subjectParser = new JsonObjectParser();
                        subjectParser.parse();
                        break;
                    }
                    case END_ARRAY: {
                        return;
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }
            }
        }

    }

    /**
     * Parses something that evaluates to a resource at object position in a statement
     */
    class ObjectParser {

        private final BlankNodeOrIRI subject;
        private final IRI predicate;

        public ObjectParser(BlankNodeOrIRI subject, IRI predicate) {
            this.subject = subject;
            this.predicate = predicate;
        }

        void parse() {
            final Event firstEvent = jsonParser.next();
            parse(firstEvent);
        }

        void parse(final Event firstEvent) {
            switch (firstEvent) {
                case START_OBJECT: {
                    parseSingleObject();
                    break;
                }
                case START_ARRAY: {
                    parseArray();
                    break;
                }
                case VALUE_STRING: {
                    sink.add(new TripleImpl(subject, predicate, new PlainLiteralImpl(jsonParser.getString())));
                    break;
                }
                default: {
                    throw new RuntimeException("Currently only documents starting with resorce are supported, got: " + firstEvent);
                }
            }
        }

        private void parseSingleObject() {
            final JsonObjectParser subjectParser = new JsonObjectParser();
            subjectParser.parse();
            sink.add(new TripleImpl(subject, predicate, subjectParser.node));
        }

        private void parseArray() {
            while (jsonParser.hasNext()) {
                final Event next = jsonParser.next();
                switch (next) {
                    case START_OBJECT: {
                        parseSingleObject();
                        break;
                    }
                    case VALUE_STRING: {
                        sink.add(new TripleImpl(subject, predicate, new PlainLiteralImpl(jsonParser.getString())));
                        break;
                    }
                    case END_ARRAY: {
                        return;
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }
            }
        }

    }

    public class ContextParser {

        public ContextParser() {
        }

        Context parse() {
            final Context result = new Context(context);
            JsonParser.Event firstKey = jsonParser.next();
            /*its value MUST be null, an absolute IRI, a relative IRI, a context 
            definition, or an array composed of any of these.*/
            switch (firstKey) {
                case START_OBJECT: {
                    //its a context definition
                    break;
                }
                default:
                    throw new RuntimeException("Sorry we currently supoort only inline context definitions");
            }
            while (jsonParser.hasNext()) {
                final JsonParser.Event next = jsonParser.next();
                switch (next) {
                    case KEY_NAME: {
                        handleKey(result);
                        break;
                    }
                    case END_OBJECT: {
                        return result;
                    }
                    default: {
                        throw new RuntimeException("Not supported here: " + next);
                    }
                }
            }
            throw new RuntimeException("Unexpected end of JSON data");
        }

        private void handleKey(Context target) {
            final String term = jsonParser.getString();
            JsonParser.Event valueEvent = jsonParser.next();
            switch (valueEvent) {
                case VALUE_STRING: {
                    //TODO handle null
                    //TODO apparently terms can be defined using terms from the same context as long as its not circular
                    BlankNodeOrIRI value = parseKeyOrType(jsonParser.getString());
                    target.register(term, value);
                    break;
                }
                case START_OBJECT: {
                    throw new RuntimeException("Expanded term definition not yet supported");
                }
            }
        }

    }

    static class Context {

        private final Map<String, BlankNodeOrIRI> termMap = new HashMap<>();
        private final Context parent;
        private URL baseURL;

        public Context() {
            parent = null;
        }

        public Context(Context parent) {
            this.parent = parent;
        }

        BlankNodeOrIRI resolveKeyOrType(String key) {
            final BlankNodeOrIRI exactMatch = getExactMatch(key);
            if (exactMatch != null) {
                return exactMatch;
            }
            final int colonPos = key.indexOf(':');
            if (colonPos > -1) {
                return resolveCurie(key);
            }
            //TODO prepend vocab, ignore if not resolvable
            if (parent == null) {
                return new IRI(key);
            } else {
                return parent.resolveKeyOrType(key);
            }
        }

        BlankNodeOrIRI resolveRelativeUriOrCurie(String key) {
            final int colonPos = key.indexOf(':');
            if (colonPos > -1) {
                return resolveCurie(key);
            }
            return resolveRelativeUri(key);
        }

        private BlankNodeOrIRI resolveCurie(String key) {
            final int colonPos = key.indexOf(':');
            if (colonPos > -1) {
                final String prefix = key.substring(0, colonPos);
                final IRI expanded = (IRI) termMap.get(prefix);
                if (expanded != null) {
                    return new IRI(expanded.getUnicodeString() + key.substring(colonPos + 1));
                }
            }
            if (parent == null) {
                return new IRI(key);
            } else {
                //TODO handle case prefix has been explicitely set to null
                return parent.resolveCurie(key);
            }
        }

        private BlankNodeOrIRI getExactMatch(String key) {
            final BlankNodeOrIRI value = termMap.get(key);
            if (value != null) {
                return value;
            }
            if (parent != null) {
                return parent.getExactMatch(key);
            } else {
                return null;
            }
        }

        private BlankNodeOrIRI resolveRelativeUri(String key) {
            if (baseURL != null) {
                try {
                    return new IRI(new URL(baseURL, key).toString());
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            } else if (parent != null) {
                return parent.resolveRelativeUri(key);
            } else {
                return new IRI(key);
            }
        }

        private void register(String term, BlankNodeOrIRI value) {
            termMap.put(term, value);
        }

        private void setBaseIRI(IRI base) {
            if (base != null) {
                try {
                    this.baseURL = new URL(base.getUnicodeString());
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

}
