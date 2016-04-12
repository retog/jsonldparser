# jsonldparser

Efficient [JSON-LD](http://json-ld.org/) parsing.

Currently **supports only a subset of the JSON-LD format**. 

Notable limitations:
- The `@id` and `@context` are only allowed as the first keys of an object
- Named graphs are not supported, so the `@graph` keyword is only allowed in the top-level object
- The value of `@context` key must be a context definition
 

## So what can it do

- Parse rather large files (900MB in less than 15s on the command line of my laptop)
- See the files in [src/test/resources/com/zazuko/jsonld/parser](src/test/resources/com/zazuko/jsonld/parser) for example of files that it can parse.
 
## How to use it

It can be used programmatically or on the command line. In both cases you'll 
want to build it first using maven:

    mvn install

### Command line usage

After building you'll find a file named `jsonld-parser-[VERSION].jar` simply 
invoke this file with `java -jar` and specify the path to your JSON-LD file as 
argument.

    cd target
    java -jar jsonld-parser-*.jar ./test-classes/com/zazuko/jsonld/parser/knows-circle.json


### Programmatic usage

The following lines from the test illustrate the programmatic usage:

```java
final InputStream inJsonLd = getClass().getResourceAsStream(baseName+".json");
final Graph graph = new SimpleGraph();
JsonLdParser.parse(inJsonLd, graph);
```

Note that you don't need to pass a graph but you can pass an instance of [TripleSink](src/main/java/com/zazuko/jsonld/parser/TripleSink.java) for efficient streaming parsing.
