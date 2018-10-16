package net.kilink.jackson;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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
    public void testSerializerGeneration() {
        ObjectMapper mapper = new ObjectMapper();

        String generatedSerializer = Util.generateSerializer(Foo.class, mapper.getSerializationConfig());

        System.out.println(generatedSerializer);

        Compilation result = javac()
                .compile(JavaFileObjects.forSourceString(
                        "com.kilink.net.serializers.FooSerializer", generatedSerializer));

        assertThat(result).succeededWithoutWarnings();
    }
}
