package net.kilink.jackson;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static net.kilink.jackson.TestUtils.loadClass;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SerializerTest {

    public static final class Foo {

        private String bar;
        private int baz;
        private List<String> things;
        private Map<String, String> otherThings;
        private boolean enabled = false;

        public Foo() { }

        @JsonProperty("renamedBar")
        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }

        public int getBaz() {
            return baz;
        }

        public void setBaz(int baz) {
            this.baz = baz;
        }

        public List<String> getThings() {
            return things;
        }

        public void setThings(List<String> things) {
            this.things = things;
        }

        public Map<String, String> getOtherThings() {
            return otherThings;
        }

        public void setOtherThings(Map<String, String> otherThings) {
            this.otherThings = otherThings;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Test
    public void testSerializerGeneration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String generatedSerializer = Util.generateSerializer(Foo.class, mapper.getSerializationConfig());
        JavaFileObject sourceFile = JavaFileObjects.forSourceString(
                "com.kilink.net.serializers.FooSerializer", generatedSerializer);

        Compilation result = javac().compile(sourceFile);
        assertThat(result).succeededWithoutWarnings();

        JavaFileObject classFile = result.generatedFiles().get(0);
        Class<JsonSerializer<Foo>> serializerClass = loadClass(classFile);

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

        String serializedJson = mapper.writeValueAsString(foo);
        JsonNode jsonNode = mapper.readTree(serializedJson);

        assertEquals(foo.getBaz(), jsonNode.at("/baz").asInt());
        assertEquals(foo.getBar(), jsonNode.at("/renamedBar").asText());
        assertEquals(foo.isEnabled(), jsonNode.at("/enabled").asBoolean());

        List<String> things = StreamSupport.stream(jsonNode.at("/things").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
        assertEquals(foo.getThings(), things);

        Map<String, String> otherThings = mapper.convertValue(jsonNode.at("/otherThings"), new TypeReference<Map<String, String>>() {});
        assertEquals(foo.getOtherThings(), otherThings);
    }
}
