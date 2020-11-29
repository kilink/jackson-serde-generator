package net.kilink.jackson;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

public class ModuleGeneratorTest {
    @Test
    public void testGenerate() {
        String packageName = "net.kilink.jackson";
        String moduleName = "FooBarModule";
        ModuleGenerator moduleGenerator = ModuleGenerator.builder()
                .withModuleName(ClassName.get(packageName, moduleName))
                .withSerializer(ClassName.get(packageName, "FooSerializer"))
                .withSerializer(ClassName.get(packageName, "BarSerializer"))
                .withDeserializer(ClassName.get(packageName, "Foo"), ClassName.get(packageName, "FooDeserializer"))
                .withDeserializer(ClassName.get(packageName, "Bar"), ClassName.get(packageName, "BarDeserializer"))
                .build();
        JavaFile javaFile = moduleGenerator.generate();

        assertThat(javaFile.toString()).isEqualTo("package net.kilink.jackson;\n" +
                "\n" +
                "import com.fasterxml.jackson.databind.Module;\n" +
                "import com.fasterxml.jackson.databind.module.SimpleModule;\n" +
                "import com.google.auto.service.AutoService;\n" +
                "\n" +
                "@AutoService(Module.class)\n" +
                "final class FooBarModule extends SimpleModule {\n" +
                "  {\n" +
                "    addSerializer(new FooSerializer());\n" +
                "    addSerializer(new BarSerializer());\n" +
                "    addDeserializer(Foo.class, new FooDeserializer());\n" +
                "    addDeserializer(Bar.class, new BarDeserializer());\n" +
                "  }\n" +
                "}\n");
    }
}
