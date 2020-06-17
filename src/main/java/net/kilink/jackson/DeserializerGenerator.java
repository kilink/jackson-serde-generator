package net.kilink.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DeserializerGenerator<T> {

    private final DeserializationConfig deserializationConfig;
    private final Class<T> clazz;
    private final String packageName;
    private final String deserializerClassName;
    private final NameAllocator nameAllocator = new NameAllocator();

    private DeserializerGenerator(Builder<T> builder) {
        this.deserializationConfig = builder.deserializationConfig;
        this.clazz = builder.klazz;
        this.packageName = builder.packageName;
        this.deserializerClassName = builder.deserializerClassName;
    }

    public static <T> Builder<T> builderFor(Class<T> klazz) {
        return new Builder<>(klazz);
    }

    public JavaFileObject generateDeserializer() {
        BasicBeanDescription beanDescription = deserializationConfig.introspect(
                deserializationConfig.getTypeFactory().constructType(clazz));
        JsonIgnoreProperties.Value ignoreProperties = deserializationConfig.getDefaultPropertyIgnorals(
                beanDescription.getBeanClass(), beanDescription.getClassInfo());

        Set<String> ignoredPropertyNames = ignoreProperties.findIgnoredForDeserialization();
        List<BeanPropertyDefinition> properties = beanDescription.findProperties().stream()
                .filter(p -> !ignoredPropertyNames.contains(p.getName()))
                .collect(Collectors.toList());

        // Reserve our parameter names to avoid potential conflicts
        nameAllocator.newName("p");
        nameAllocator.newName("ctxt");

        MethodSpec.Builder deserializeMethodSpec = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(clazz)
                .addParameter(ParameterSpec.builder(JsonParser.class, "p").build())
                .addParameter(ParameterSpec.builder(DeserializationContext.class, "ctxt").build())
                .addException(IOException.class)
                .addException(JsonProcessingException.class);

        deserializeMethodSpec.beginControlFlow("if (!p.isExpectedStartObjectToken())");
        deserializeMethodSpec.addStatement("ctxt.handleUnexpectedToken(getValueType(ctxt), p)");
        deserializeMethodSpec.endControlFlow();
        deserializeMethodSpec.addCode("\n");

        AnnotatedConstructor defaultConstructor = beanDescription.findDefaultConstructor();
        if (defaultConstructor == null) {
            throw new RuntimeException("Currently only default constructors are supported");
        }

        for (BeanPropertyDefinition prop : properties) {
            String name = nameAllocator.newName(prop.getName(), prop);
            deserializeMethodSpec.addStatement("$T $L = null", getTypeName(prop), name);
        }
        deserializeMethodSpec.addCode("\n");

        String tokenName = nameAllocator.newName("token");
        deserializeMethodSpec.addStatement("$T $L = p.nextToken()", JsonToken.class, tokenName);
        deserializeMethodSpec.beginControlFlow("while ($L != null)", tokenName);

        deserializeMethodSpec.beginControlFlow("if (token == $T.$L)", JsonToken.class, JsonToken.FIELD_NAME);
        deserializeMethodSpec.addStatement("p.nextValue()");
        String fieldName = nameAllocator.newName("fieldName");
        deserializeMethodSpec.addStatement("$T $L = p.currentName()", String.class, fieldName);
        deserializeMethodSpec.beginControlFlow("switch ($L)", fieldName);

        for (BeanPropertyDefinition prop : properties) {
            deserializeMethodSpec.addCode("case $S:\n", prop.getName());

            Class<?> propClass = prop.getRawPrimaryType();
            final CodeBlock valueHandler;
            if (propClass.isAssignableFrom(String.class)) {
                valueHandler = CodeBlock.of("p.getText()");
            } else if (propClass.isAssignableFrom(Integer.class) || propClass == int.class) {
                valueHandler = CodeBlock.of("p.getIntValue()");
            } else if (propClass.isAssignableFrom(Double.class) || propClass == double.class) {
                valueHandler = CodeBlock.of("p.getDoubleValue()");
            } else if (propClass.isAssignableFrom(Boolean.class) || propClass == boolean.class) {
                valueHandler = CodeBlock.of("p.getBooleanValue()");
            } else if (propClass.isAssignableFrom(List.class) || propClass.isAssignableFrom(Map.class)) {
                TypeName typeRef = ParameterizedTypeName.get(ClassName.get(TypeReference.class), getTypeName(prop));
                valueHandler = CodeBlock.of("p.readValueAs(new $T() {})", typeRef);
            } else if (propClass.isEnum()) {
                valueHandler = CodeBlock.of("$T.valueOf(p.getText())", propClass);
            } else {
                throw new RuntimeException("Handling of value type " + propClass + " not implemented");
            }

            deserializeMethodSpec.addCode("$>");
            deserializeMethodSpec.addStatement("$L = $L", nameAllocator.get(prop), valueHandler);

            deserializeMethodSpec.addStatement("break$<");
        }

        deserializeMethodSpec.addCode("default:\n$>");
        deserializeMethodSpec.beginControlFlow("if (!(ignoreUnknown || ignored.contains($L)))", fieldName);
        deserializeMethodSpec.addStatement("handleUnknownProperty(p, ctxt, $T.class, $L)", clazz, fieldName);
        deserializeMethodSpec.endControlFlow();

        deserializeMethodSpec.endControlFlow();
        deserializeMethodSpec.endControlFlow();
        deserializeMethodSpec.addStatement("$L = p.nextToken()", tokenName);
        deserializeMethodSpec.endControlFlow();

        String instanceName = nameAllocator.newName("obj");
        deserializeMethodSpec.addStatement("$T $L = new $T()", clazz, instanceName, clazz);

        for (BeanPropertyDefinition prop : properties) {
            deserializeMethodSpec.addStatement("$L.$L($L)", instanceName, prop.getSetter().getName(), nameAllocator.get(prop));
        }

        deserializeMethodSpec.addStatement("return $L", instanceName);

        TypeSpec.Builder deserializerClassSpec = TypeSpec.classBuilder(deserializerClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ParameterizedTypeName.get(StdDeserializer.class, clazz))
                .addField(FieldSpec.builder(
                        ParameterizedTypeName.get(Set.class, String.class),
                        "ignored",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).initializer("new $T()", ParameterizedTypeName.get(HashSet.class, String.class)).build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super($T.class)", clazz)
                        .build())
                .addMethod(deserializeMethodSpec.build());

        deserializerClassSpec.addField(
                FieldSpec.builder(boolean.class,
                        "ignoreUnknown",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", ignoreProperties.getIgnoreUnknown()).build());

        if (!ignoredPropertyNames.isEmpty()) {
            CodeBlock.Builder staticInitializer = CodeBlock.builder();
            for (String ignoredName : ignoreProperties.findIgnoredForDeserialization()) {
                staticInitializer.addStatement("ignored.add($S)", ignoredName);
            }

            deserializerClassSpec.addStaticBlock(staticInitializer.build());
        }

        JavaFile javaFile = JavaFile.builder(packageName, deserializerClassSpec.build())
                .build();
        return javaFile.toJavaFileObject();
    }

    private static TypeName getTypeName(BeanPropertyDefinition prop) {
        if (prop.getPrimaryType().hasGenericTypes()) {
            Class<?>[] genericTypeParams = prop.getPrimaryType().getBindings().getTypeParameters()
                    .stream()
                    .map(JavaType::getRawClass)
                    .toArray(Class[]::new);
            return ParameterizedTypeName.get(prop.getRawPrimaryType(), genericTypeParams);
        }
        return TypeName.get(prop.getRawPrimaryType()).box();
    }

    public static final class Builder<T> {

        private DeserializationConfig deserializationConfig = null;
        private final Class<T> klazz;
        private String packageName = null;
        private String deserializerClassName = null;

        public Builder(Class<T> klazz) {
            this.klazz = Objects.requireNonNull(klazz);
        }

        public Builder<T> deserializationConfig(DeserializationConfig deserializationConfig) {
            this.deserializationConfig = deserializationConfig;
            return this;
        }

        public Builder<T> packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder<T> deserializerClassName(String deserializerClassName) {
            this.deserializerClassName = deserializerClassName;
            return this;
        }

        public DeserializerGenerator<T> build() {
            if (deserializationConfig == null) {
                deserializationConfig = new ObjectMapper().getDeserializationConfig();
            }
            if (packageName == null) {
                packageName = klazz.getPackage().getName();
            }
            if (deserializerClassName == null) {
                deserializerClassName = klazz.getSimpleName() + "Deserializer";
            }
            return new DeserializerGenerator<>(this);
        }
    }
}
