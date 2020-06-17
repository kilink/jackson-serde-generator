package net.kilink.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import org.junit.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static net.kilink.jackson.TestUtils.loadClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeserializerGeneratorTest {
    @Test
    public void testDeserializerCompilation() {
        DeserializerGenerator<Foo> generator = DeserializerGenerator.builderFor(Foo.class).build();
        JavaFileObject deserializerSource = generator.generateDeserializer();

        Compilation result = javac().compile(deserializerSource);
        assertThat(result).succeededWithoutWarnings();
    }

    @Test
    public void testGeneratedDeserializer() throws Exception {
        DeserializerGenerator<Foo> generator = DeserializerGenerator.builderFor(Foo.class).build();
        JavaFileObject deserializerSource = generator.generateDeserializer();

        Compilation result = javac().compile(deserializerSource);
        assertThat(result).succeeded();

        result.generatedFiles().forEach(TestUtils::loadClass);

        Class<JsonDeserializer<Foo>> deserializerClass = loadClass("net.kilink.jackson.FooDeserializer");
        JsonDeserializer<Foo> deserializer = deserializerClass.newInstance();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Foo.class, deserializer);

        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerModule(module);

        Foo foo = mapper.readValue("{\"baz\": 5, \"enabled\": true, " +
                "\"things\": [1, 2, 3], \"otherThings\": {\"key\": \"value\"}, \"ignored\": 1234," +
                "\"renamedBar\": \"barValue\"}", Foo.class);
        assertEquals(5, foo.getBaz());
        assertEquals("barValue", foo.getBar());
        assertTrue(foo.isEnabled());
        assertEquals(ImmutableList.of("1", "2", "3"), foo.getThings());
        assertEquals(ImmutableMap.of("key", "value"), foo.getOtherThings());
    }
}
