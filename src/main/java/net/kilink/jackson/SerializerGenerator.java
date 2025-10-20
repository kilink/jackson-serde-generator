package net.kilink.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.checkerframework.javacutil.TypesUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.kilink.jackson.Utils.nameForGetter;

public final class SerializerGenerator {

    private final TypeElement typeElement;
    private final ProcessingEnvironment processingEnv;
    private final ClassName className;
    private final ClassName serializerClassName;
    private final SerializationConfig serializationConfig;

    public SerializerGenerator(TypeElement typeElement, ProcessingEnvironment processingEnv) {
        this.typeElement = typeElement;
        this.processingEnv = processingEnv;
        this.className = ClassName.get(typeElement);
        this.serializerClassName = getSerializerName(typeElement);
        this.serializationConfig = new ObjectMapper().getSerializationConfig();
    }

    private ClassName getSerializerName(TypeElement element) {
        AutoSerde anno = element.getAnnotation(AutoSerde.class);
        String serializerName = "";
        String packageName = "";

        if (anno != null) {
            serializerName = anno.serializerName();
            packageName = anno.packageName();
        }
        if (packageName.isEmpty()) {
            packageName = elements().getPackageOf(element).getQualifiedName().toString();
        }
        if (serializerName.isEmpty()) {
            serializerName = element.getSimpleName() + "Serializer";
        }
        return ClassName.get(packageName, serializerName);
    }

    public JavaFile generate() {
        return JavaFile.builder(serializerClassName.packageName(), buildClass()).build();
    }

    private TypeSpec buildClass() {
        return TypeSpec.classBuilder(serializerClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ParameterizedTypeName.get(ClassName.get(StdSerializer.class), className))
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super($T.class)", className)
                        .build())
                .addMethod(buildSerializeMethod())
                .build();
    }

    private MethodSpec buildSerializeMethod() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addException(IOException.class)
                .addParameter(ParameterSpec.builder(className, "value").build())
                .addParameter(ParameterSpec.builder(JsonGenerator.class, "gen").build())
                .addParameter(ParameterSpec.builder(SerializerProvider.class, "provider").build())
                .addStatement("gen.writeStartObject(value)");

        List<Getter> getters = new ArrayList<>();

        for (Element el : typeElement.getEnclosedElements()) {
            if (el.getKind() != ElementKind.METHOD || isIgnored(el)) {
                continue;
            }
            ExecutableElement element = (ExecutableElement) el;
            TypeMirror returnType = element.getReturnType();
            if (!element.getParameters().isEmpty() || returnType instanceof NoType) {
                continue;
            }
            String methodName = element.getSimpleName().toString();
            if (methodName.startsWith("get") && methodName.length() > 3) {
                if (isVisible(PropertyAccessor.GETTER, element)) {
                    getters.add(new Getter(PropertyAccessor.GETTER, element));
                }
            }
            if (methodName.startsWith("is") && methodName.length() > 2) {
                if (types().isSameType(returnType, elements().getTypeElement(Boolean.class.getCanonicalName()).asType())) {
                    if (isVisible(PropertyAccessor.IS_GETTER, element)) {
                        getters.add(new Getter(PropertyAccessor.IS_GETTER, element));
                    }
                }
            }
        }

        PropertyNamingStrategy namingStrategy = getPropertyNamingStrategy();

        for (Getter getter : getters) {
            String fieldName = getExplicitPropertyName(getter.element());
            if (fieldName == null) {
                fieldName = nameForGetter(getter.accessorType(), getter.element());
                if (fieldName != null) {
                    fieldName = namingStrategy.nameForField(serializationConfig, null, fieldName);
                }
            }

            JsonInclude.Value inclusion = getInclusion(getter.element());

            CodeBlock getValue = CodeBlock.of("value.$L()", getter.getName());
            CodeBlock genMethodCall = writeSerializedValue(getter.getReturnType(), getValue);

            if (inclusion.getValueInclusion() == JsonInclude.Include.NON_NULL && !TypesUtils.isPrimitive(getter.getReturnType())) {
                method.beginControlFlow("if ($L != null)", getValue);
                method.addStatement("gen.writeFieldName($S)", fieldName);
                method.addCode(genMethodCall);
                method.endControlFlow();
            } else {
                method.addStatement("gen.writeFieldName($S)", fieldName);
                if (!TypesUtils.isPrimitive(getter.getReturnType())) {
                    method.beginControlFlow("if ($L == null)", getValue);
                    method.addStatement("gen.writeNull()");
                    method.nextControlFlow("else");
                    method.addCode(genMethodCall);
                    method.endControlFlow();
                } else {
                    method.addCode(genMethodCall);
                }
            }
        }
        method.addStatement("gen.writeEndObject()");

        return method.build();
    }

    private CodeBlock writeSerializedValue(TypeMirror type, CodeBlock getValue) {
        if (TypesUtils.isPrimitive(type) || TypesUtils.isBoxedPrimitive(type)) {
            if (TypesUtils.isBooleanType(type)) {
                return CodeBlock.builder()
                        .addStatement("gen.writeBoolean($L)", getValue)
                        .build();
            } else if (TypesUtils.isNumeric(type)) {
                return CodeBlock.builder()
                        .addStatement("gen.writeNumber($L)", getValue)
                        .build();
            }
        } else if (TypesUtils.isString(type)) {
            return CodeBlock.builder()
                    .addStatement("gen.writeString($L)", getValue)
                    .build();
        } else if (types().isAssignable(types().erasure(type), elements().getTypeElement(Collection.class.getCanonicalName()).asType())) {
            TypeMirror itemType = ((DeclaredType) type).getTypeArguments().get(0);
            return CodeBlock.builder()
                    .addStatement("gen.writeStartArray()")
                    .beginControlFlow("for ($T item : $L)", itemType, getValue)
                    .add(writeSerializedValue(itemType, CodeBlock.of("item")))
                    .endControlFlow()
                    .addStatement("gen.writeEndArray()")
                    .build();
        } else if (types().isAssignable(types().erasure(type), elements().getTypeElement(Map.class.getCanonicalName()).asType())) {
            TypeMirror keyType = ((DeclaredType) type).getTypeArguments().get(0);
            TypeMirror valueType = ((DeclaredType) type).getTypeArguments().get(1);
            TypeName mapEntry = ParameterizedTypeName.get(ClassName.get(Map.Entry.class),
                    ClassName.get(keyType), ClassName.get(valueType));
            return CodeBlock.builder()
                    .addStatement("gen.writeStartObject()")
                    .beginControlFlow("for ($T entry : $L.entrySet())", mapEntry, getValue)
                    .addStatement("gen.writeFieldName(entry.getKey())")
                    .add(writeSerializedValue(valueType, CodeBlock.of("entry.getValue()")))
                    .endControlFlow()
                    .addStatement("gen.writeEndObject()")
                    .build();
        } else if (types().isAssignable(type,
                types().erasure(elements().getTypeElement(Enum.class.getCanonicalName()).asType()))) {

            if (serializationConfig.isEnabled(SerializationFeature.WRITE_ENUMS_USING_INDEX)) {
                return  CodeBlock.builder()
                        .addStatement("gen.writeNumber($L.ordinal())", getValue)
                        .build();
                
            } else if (serializationConfig.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
                return  CodeBlock.builder()
                        .addStatement("gen.writeString($L.toString())", getValue)
                        .build();
            } else {
                List<VariableElement> enumValues = new ArrayList<>();
                boolean hasAnyExplicitNames = false;
                for (Element e : elements().getAllMembers((TypeElement) types().asElement(type))) {
                    if (e.getKind() == ElementKind.ENUM_CONSTANT) {
                        if (!hasAnyExplicitNames && getExplicitPropertyName(e) != null) {
                            hasAnyExplicitNames = true;
                        }
                        enumValues.add((VariableElement) e);
                    }
                }

                if (hasAnyExplicitNames) {
                    CodeBlock.Builder enumSwitch = CodeBlock.builder()
                            .beginControlFlow("switch ($L)", getValue);
                    for (VariableElement e : enumValues) {
                        enumSwitch.add("case $L:\n", e.getSimpleName());
                        String explicitName = getExplicitPropertyName(e);
                        if (explicitName != null) {
                            enumSwitch.addStatement("$>gen.writeString($S)$<", explicitName);
                        } else {
                            enumSwitch.addStatement("$>gen.writeString($L.name())$<", getValue);
                        }
                    }

                    enumSwitch.endControlFlow();
                    return enumSwitch.build();
                }
                return CodeBlock.builder()
                        .addStatement("gen.writeString($L.name())", getValue)
                        .build();
            }
        }
        return CodeBlock.builder()
                .addStatement("gen.writeObject($L)", getValue)
                .build();
    }

    private boolean isIgnored(Element element) {
        if (serializationConfig.isEnabled(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                && element.getModifiers().contains(Modifier.TRANSIENT)) {
            return true;
        }
        JsonIgnore ignore = element.getAnnotation(JsonIgnore.class);
        if (ignore != null) {
            return ignore.value();
        }
        return false;
    }

    private static JsonInclude.Value getInclusion(Element element) {
        JsonInclude anno = element.getAnnotation(JsonInclude.class);
        return (anno == null) ? JsonInclude.Value.empty() : JsonInclude.Value.construct(anno.value(), anno.content());
    }

    @Nullable
    private String getExplicitPropertyName(Element element) {
        JsonProperty anno = element.getAnnotation(JsonProperty.class);
        if (anno != null && !anno.value().equals(JsonProperty.USE_DEFAULT_NAME)) {
            return anno.value();
        }
        return null;
    }

    private boolean isVisible(PropertyAccessor accessorType, Element element) {
        JsonAutoDetect.Value value = JsonAutoDetect.Value.defaultVisibility();
        if (!serializationConfig.isEnabled(MapperFeature.AUTO_DETECT_FIELDS)) {
            value = value.withFieldVisibility(JsonAutoDetect.Visibility.NONE);
        }
        if (!serializationConfig.isEnabled(MapperFeature.AUTO_DETECT_GETTERS)) {
            value = value.withGetterVisibility(JsonAutoDetect.Visibility.NONE);
        }
        if (!serializationConfig.isEnabled(MapperFeature.AUTO_DETECT_IS_GETTERS)) {
            value = value.withIsGetterVisibility(JsonAutoDetect.Visibility.NONE);
        }
        if (!serializationConfig.isEnabled(MapperFeature.AUTO_DETECT_SETTERS)) {
            value = value.withSetterVisibility(JsonAutoDetect.Visibility.NONE);
        }
        if (!serializationConfig.isEnabled(MapperFeature.AUTO_DETECT_CREATORS)) {
            value = value.withCreatorVisibility(JsonAutoDetect.Visibility.NONE);
        }

        JsonAutoDetect anno = typeElement.getAnnotation(JsonAutoDetect.class);
        if (anno != null) {
            value = value.withOverrides(JsonAutoDetect.Value.from(anno));
        }

        JsonAutoDetect.Visibility visibility = switch (accessorType) {
            case FIELD -> value.getFieldVisibility();
            case GETTER -> value.getGetterVisibility();
            case IS_GETTER -> value.getIsGetterVisibility();
            case CREATOR -> value.getCreatorVisibility();
            default -> throw new IllegalArgumentException();
        };

        Set<Modifier> modifiers = element.getModifiers();

        boolean packagePrivate = Collections.disjoint(modifiers,
                EnumSet.of(Modifier.PUBLIC, Modifier.PRIVATE, Modifier.PROTECTED));
        boolean packagePrivateVisible = packagePrivate && Objects.equals(className.packageName(),
                elements().getPackageOf(element).getQualifiedName().toString());

        return switch (visibility) {
            case ANY, NON_PRIVATE -> modifiers.contains(Modifier.PUBLIC) || packagePrivateVisible;
            case PROTECTED_AND_PUBLIC, PUBLIC_ONLY -> modifiers.contains(Modifier.PUBLIC);
            default -> false;
        };
    }

    private final List<PropertyNamingStrategy> propertyNamingStrategies = List.of(
            PropertyNamingStrategy.KEBAB_CASE,
            PropertyNamingStrategy.LOWER_CAMEL_CASE,
            PropertyNamingStrategy.LOWER_DOT_CASE,
            PropertyNamingStrategy.SNAKE_CASE,
            PropertyNamingStrategy.UPPER_CAMEL_CASE,
            PropertyNamingStrategies.KEBAB_CASE,
            PropertyNamingStrategies.LOWER_CAMEL_CASE,
            PropertyNamingStrategies.LOWER_DOT_CASE,
            PropertyNamingStrategies.SNAKE_CASE,
            PropertyNamingStrategies.UPPER_CAMEL_CASE);

    private PropertyNamingStrategy getPropertyNamingStrategy() {
        TypeMirror jsonNaming = elements().getTypeElement(JsonNaming.class.getCanonicalName()).asType();
        for (AnnotationMirror anno : elements().getAllAnnotationMirrors(typeElement)) {
            if (types().isSameType(anno.getAnnotationType(), jsonNaming)) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elements().getElementValuesWithDefaults(anno).entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        AnnotationValue value = entry.getValue();
                        TypeMirror t = (TypeMirror) value.getValue();
                        for (PropertyNamingStrategy strategy : propertyNamingStrategies) {
                           if (types().isSameType(t, elements().getTypeElement(strategy.getClass().getCanonicalName()).asType())) {
                               return strategy;
                            }
                        }
                    }
                }
            }
        }
        return serializationConfig.getPropertyNamingStrategy();
    }

    private Elements elements() {
        return processingEnv.getElementUtils();
    }

    private Types types() {
        return processingEnv.getTypeUtils();
    }

    private record Getter(PropertyAccessor accessorType, ExecutableElement element) {

        public String getName() {
            return element.getSimpleName().toString();
        }
        public TypeMirror getReturnType() {
            return element.getReturnType();
        }
    }
}
