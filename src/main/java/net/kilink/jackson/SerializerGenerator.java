package net.kilink.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.EnumValues;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Objects;

public final class SerializerGenerator<T> {

    private final Class<T> clazz;
    private final SerializationConfig serializationConfig;
    private final String packageName;
    private final String serializerName;

    private SerializerGenerator(Builder<T> builder) {
        this.clazz = builder.clazz;
        this.serializationConfig = builder.serializationConfig;
        this.packageName = builder.packageName;
        this.serializerName = builder.serializerName;
    }

    public static <T> Builder<T> builderFor(Class<T> klazz) {
        return new Builder<>(klazz);
    }

    public JavaFileObject generateSerializer() {
        BasicBeanDescription beanDescription = serializationConfig.introspect(
                serializationConfig.getTypeFactory().constructType(clazz));

        MethodSpec.Builder serializeMethod = MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addException(IOException.class)
                .addParameter(ParameterSpec.builder(clazz, "value").build())
                .addParameter(ParameterSpec.builder(JsonGenerator.class, "gen").build())
                .addParameter(ParameterSpec.builder(SerializerProvider.class, "provider").build())
                .addStatement("gen.writeStartObject(value)");

        for (BeanPropertyDefinition prop : beanDescription.findProperties()) {
            serializeMethod.addStatement("gen.writeFieldName($S)", prop.getName());
            writeValue(serializationConfig, prop.getPrimaryType(), CodeBlock.of("value.$L()", prop.getGetter().getName()), serializeMethod);
        }

        serializeMethod.addStatement("gen.writeEndObject()");

        TypeSpec serializerSpec = TypeSpec.classBuilder(serializerName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ParameterizedTypeName.get(StdSerializer.class, clazz))
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super($T.class)", clazz)
                        .build())
                .addMethod(serializeMethod.build())
                .build();

        String packageName = clazz.getPackage().getName();
        return JavaFile.builder(packageName, serializerSpec)
                .build()
                .toJavaFileObject();
    }

    private static void writeValue(SerializationConfig serializationConfig, JavaType type, CodeBlock codeBlock, MethodSpec.Builder methodSpec) {
        if (type.isTypeOrSubTypeOf(String.class)) {
            methodSpec.addStatement("gen.writeString($L)", codeBlock);
        } else if (isNumber(type)) {
            methodSpec.addStatement("gen.writeNumber($L)", codeBlock);
        } else if (type.getRawClass().equals(Boolean.class) || type.getRawClass().equals(boolean.class)) {
            methodSpec.addStatement("gen.writeBoolean($L)", codeBlock);
        } else if (type.isCollectionLikeType()) {
            methodSpec.addStatement("gen.writeStartArray()");

            methodSpec.beginControlFlow("for ($T item : $L)",
                    type.getContentType().getRawClass(),
                    codeBlock);

            writeValue(serializationConfig, type.getContentType(), CodeBlock.of("item"), methodSpec);

            methodSpec.endControlFlow();
            methodSpec.addStatement("gen.writeEndArray()");
        } else if (type.isEnumType()) {
            @SuppressWarnings("unchecked")
            Class<Enum<?>> enumClass = (Class<Enum<?>>) type.getRawClass();
            EnumValues enumValues = EnumValues.construct(serializationConfig, enumClass);

            methodSpec.beginControlFlow("switch ($L)", codeBlock);
            for (Enum<?> enumValue : enumValues.enums()) {
                methodSpec.addCode("case $L:\n", enumValue);
                methodSpec.addStatement("$>gen.writeString($S)", enumValues.serializedValueFor(enumValue));
                methodSpec.addStatement("break$<");
            }

            methodSpec.addCode("default:\n");
            methodSpec.addStatement("$>gen.writeNull()$<");
            methodSpec.endControlFlow();
        } else {
            methodSpec.addStatement("gen.writeObject($L)", codeBlock);
        }
    }

    private static boolean isNumber(JavaType type) {
        return type.isTypeOrSubTypeOf(Number.class) ||
                (type.isPrimitive() &&
                        type.getRawClass().equals(int.class) ||
                        type.getRawClass().equals(long.class) ||
                        type.getRawClass().equals(short.class) ||
                        type.getRawClass().equals(double.class) ||
                        type.getRawClass().equals(float.class));
    }

    public static final class Builder<T> {

        private final Class<T> clazz;
        private SerializationConfig serializationConfig;
        private String packageName;
        private String serializerName;

        private Builder(Class<T> clazz) {
            this.clazz = Objects.requireNonNull(clazz);
        }

        public Builder<T> serializationConfig(SerializationConfig serializationConfig) {
            this.serializationConfig = serializationConfig;
            return this;
        }

        public Builder<T> packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder<T> serializerName(String serializerName) {
            this.serializerName = serializerName;
            return this;
        }

        public SerializerGenerator<T> build() {
            if (serializationConfig == null) {
                serializationConfig = new ObjectMapper().getSerializationConfig();
            }
            if (packageName == null) {
                packageName = clazz.getPackage().getName();
            }
            if (serializerName == null) {
                serializerName = clazz.getSimpleName() + "Serializer";
            }
            return new SerializerGenerator<>(this);
        }
    }
}
