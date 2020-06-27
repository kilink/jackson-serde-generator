package net.kilink.jackson;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModuleGenerator {

    private final ClassName name;
    private final Collection<ClassName> serializers;
    private final Map<ClassName, ClassName> deserializers;

    private ModuleGenerator(Builder builder) {
        this.name = builder.moduleName;
        this.serializers = builder.serializers;
        this.deserializers = builder.deserializers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public JavaFile generate() {
        return JavaFile.builder(name.packageName(), buildClass())
                .build();
    }

    private TypeSpec buildClass() {
        CodeBlock.Builder init = CodeBlock.builder();
        for (ClassName serializer : serializers) {
            init.addStatement("addSerializer(new $T())", serializer);
        }

        for (Map.Entry<ClassName, ClassName> entry : deserializers.entrySet()) {
            init.addStatement("addDeserializer($T.class, new $T())", entry.getKey(), entry.getValue());
        }

        return TypeSpec.classBuilder(name)
                .addAnnotation(AnnotationSpec.builder(AutoService.class)
                        .addMember("value", "$T.class", Module.class)
                        .build())
                .superclass(SimpleModule.class)
                .addModifiers(Modifier.FINAL)
                .addInitializerBlock(init.build())
                .build();
    }

    public static final class Builder {

        private ClassName moduleName;
        private final List<ClassName> serializers = new ArrayList<>();
        private final Map<ClassName, ClassName> deserializers = new LinkedHashMap<>();

        public Builder withModuleName(ClassName moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder withSerializer(ClassName serializer) {
            serializers.add(serializer);
            return this;
        }

        public Builder withSerializers(Collection<ClassName> serializers) {
            this.serializers.addAll(serializers);
            return this;
        }

        public Builder withDeserializer(ClassName className, ClassName deserializerName) {
            this.deserializers.put(className, deserializerName);
            return this;
        }

        public ModuleGenerator build() {
            Objects.requireNonNull(moduleName);
            return new ModuleGenerator(this);
        }
    }
}
