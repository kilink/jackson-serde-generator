package net.kilink.jackson;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static net.kilink.jackson.TestUtils.loadClass;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SerializerGeneratorTest {

    @Test
    public void testSerializerCompilation() {
        SerializerGenerator<Foo> generator = SerializerGenerator.builderFor(Foo.class).build();
        JavaFileObject sourceFile = generator.generateSerializer();

        Compilation result = javac().compile(sourceFile);
        assertThat(result).succeededWithoutWarnings();
    }

    @Test
    public void testGeneratedSerializer() throws Exception {
        SerializerGenerator<Foo> generator = SerializerGenerator.builderFor(Foo.class).build();
        ObjectMapper mapper = new ObjectMapper();
        JavaFileObject sourceFile = generator.generateSerializer();

        Compilation result = javac().compile(sourceFile);
        assertThat(result).succeeded();

        for (JavaFileObject classFile : result.generatedFiles()) {
            loadClass(classFile);
        }

        Class<JsonSerializer<Foo>> serializerClass = loadClass("net.kilink.jackson.FooSerializer");

        JsonSerializer<Foo> serializer = serializerClass.newInstance();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Foo.class, serializer);
        mapper.registerModule(module);
        mapper.disable(MapperFeature.AUTO_DETECT_CREATORS,
                MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS,
                MapperFeature.AUTO_DETECT_SETTERS,
                MapperFeature.AUTO_DETECT_IS_GETTERS,
                MapperFeature.USE_ANNOTATIONS);

        Foo foo = new Foo();
        foo.setBaz(42);
        foo.setBar("barValue");
        foo.setEnabled(true);
        foo.setThings(ImmutableList.of("foo", "bar" , "baz"));
        foo.setOtherThings(ImmutableMap.of("key", "value"));
        foo.setState(Foo.State.IN_PROGRESS);

        String serializedJson = mapper.writeValueAsString(foo);
        JsonNode jsonNode = mapper.readTree(serializedJson);

        assertEquals(foo.getBaz(), jsonNode.at("/baz").asInt());
        assertEquals(foo.getBar(), jsonNode.at("/renamedBar").asText());
        assertEquals(foo.isEnabled(), jsonNode.at("/enabled").asBoolean());
        assertEquals("In Progress", jsonNode.at("/state").asText());

        List<String> things = StreamSupport.stream(jsonNode.at("/things").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
        assertEquals(foo.getThings(), things);

        Map<String, String> otherThings = mapper.convertValue(jsonNode.at("/otherThings"), new TypeReference<Map<String, String>>() {});
        assertEquals(foo.getOtherThings(), otherThings);
    }
}
