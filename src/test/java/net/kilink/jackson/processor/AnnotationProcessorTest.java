package net.kilink.jackson.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import net.kilink.jackson.AnnotationProcessor;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class AnnotationProcessorTest {
    @Test
    public void testAnnotationProcessing() {
        Compilation result = javac().withProcessors(new AnnotationProcessor())
                .compile(JavaFileObjects.forResource("Pojo.java"));

        assertThat(result).succeededWithoutWarnings();
    }
}
