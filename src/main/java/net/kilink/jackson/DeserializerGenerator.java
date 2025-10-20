package net.kilink.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.checkerframework.javacutil.TypesUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.kilink.jackson.Utils.nameForSetter;

public final class DeserializerGenerator {

    private final TypeElement typeElement;
    private final ClassName className;
    private final ProcessingEnvironment processingEnv;
    private final DeserializationConfig deserializationConfig;
    private final ClassName deserializerClassName;
    private final NameAllocator names = new NameAllocator();

    public DeserializerGenerator(TypeElement typeElement, ProcessingEnvironment processingEnv) {
        this.typeElement = typeElement;
        this.className = ClassName.get(typeElement);
        this.processingEnv = processingEnv;
        this.deserializationConfig = new ObjectMapper().getDeserializationConfig();
        this.deserializerClassName = getDeserializerName(typeElement);
    }

    private ClassName getDeserializerName(TypeElement element) {
        AutoSerde anno = element.getAnnotation(AutoSerde.class);
        String deserializerName = "";
        String packageName = "";

        if (anno != null) {
            deserializerName = anno.deserializerName();
            packageName = anno.packageName();
        }
        if (packageName.isEmpty()) {
            packageName = elements().getPackageOf(element).getQualifiedName().toString();
        }
        if (deserializerName.isEmpty()) {
            deserializerName = element.getSimpleName() + "Deserializer";
        }
        return ClassName.get(packageName, deserializerName);
    }

    public JavaFile generate() {
        return JavaFile.builder(deserializerClassName.packageName(), buildClass()).build();
    }

    private TypeSpec buildClass() {
        TypeSpec.Builder classSpec = TypeSpec.classBuilder(deserializerClassName)
                .addModifiers(Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(ClassName.get(StdDeserializer.class), className))
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super($T.class)", className)
                        .build());

        JsonIgnoreProperties.Value base = JsonIgnoreProperties.Value.forIgnoreUnknown(
                deserializationConfig.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        JsonIgnoreProperties.Value ignoreProperties = JsonIgnoreProperties.Value.merge(base, getIgnoredProperties());
        classSpec.addField(
                FieldSpec.builder(boolean.class,
                        "ignoreUnknown",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", ignoreProperties.getIgnoreUnknown()).build());

        if (ignoreProperties.getIgnored().isEmpty()) {
            classSpec.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(Set.class, String.class),
                    "ignored",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of()", Set.class).build());
        } else {
            CodeBlock properties = ignoreProperties.getIgnored().stream()
                    .map(property -> CodeBlock.of("$S", property))
                    .collect(CodeBlock.joining(", "));
            classSpec.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(Set.class, String.class),
                    "ignored",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of($L)", Set.class, properties).build());
        }
        classSpec.addMethod(buildDeserializeMethod());
        return classSpec.build();
    }

    private MethodSpec buildDeserializeMethod() {
        names.newName("p");
        names.newName("ctxt");

        MethodSpec.Builder method = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(className)
                .addParameter(ParameterSpec.builder(JsonParser.class, "p").build())
                .addParameter(ParameterSpec.builder(DeserializationContext.class, "ctxt").build())
                .addException(IOException.class)
                .addException(JsonProcessingException.class);

        method.beginControlFlow("if (!p.isExpectedStartObjectToken())");
        method.addStatement("ctxt.handleUnexpectedToken(getValueType(ctxt), p)");
        method.endControlFlow();
        method.addCode("\n");

        ExecutableElement defaultConstructor = null;
        for (Element el : typeElement.getEnclosedElements()) {
            if (el.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) el;
            if (constructor.isDefault() || constructor.getParameters().isEmpty()) {
                defaultConstructor = constructor;
                break;
            }
        }

        if (defaultConstructor == null) {
            throw new RuntimeException("Currently only default constructors are supported");
        }

        List<ExecutableElement> setters = new ArrayList<>();
        for (Element el : typeElement.getEnclosedElements()) {
            if (el.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement setter = (ExecutableElement) el;
            if (!(setter.getParameters().size() == 1 && setter.getReturnType().getKind() == TypeKind.VOID)) {
                continue;
            }
            String setterName = setter.getSimpleName().toString();
            if (!(setterName.startsWith("set") && setterName.length() > 3)) {
                continue;
            }
            setters.add(setter);
        }

        String instanceName = names.newName("obj");
        method.addStatement("$T $L = new $T()", className, instanceName, className);

        String tokenName = names.newName("token");
        method.addStatement("$T $L = p.nextToken()", JsonToken.class, tokenName);
        method.beginControlFlow("while ($L != null)", tokenName);

        method.beginControlFlow("if (token == $T.$L)", JsonToken.class, JsonToken.FIELD_NAME);
        method.addStatement("p.nextValue()");
        String fieldName = names.newName("fieldName");
        method.addStatement("$T $L = p.currentName()", String.class, fieldName);

        method.beginControlFlow("switch ($L)", fieldName);

        for (ExecutableElement setter : setters) {
            String propertyName = nameForSetter(setter);
            method.addCode("case $S:\n$>", propertyName);
            TypeMirror type = setter.getParameters().get(0).asType();
            CodeBlock reader = valueHandler(type);
            method.addStatement("$L.$L($L)", instanceName, setter.getSimpleName(), reader);

            method.addStatement("break$<");
        }

        method.addCode("default:\n$>");
        method.beginControlFlow("if (!(ignoreUnknown || ignored.contains($L)))", fieldName);
        method.addStatement("handleUnknownProperty(p, ctxt, $T.class, $L)", typeElement, fieldName);
        method.endControlFlow();

        method.endControlFlow();
        method.endControlFlow();
        method.addStatement("$L = p.nextToken()", tokenName);
        method.endControlFlow();

        method.addStatement("return $L", instanceName);

        return method.build();
    }

    private CodeBlock valueHandler(TypeMirror type) {
        if (TypesUtils.isPrimitive(type) || TypesUtils.isBoxedPrimitive(type)) {
            TypeKind kind = TypesUtils.isBoxedPrimitive(type) ? types().unboxedType(type).getKind() : type.getKind();
            return switch (kind) {
                case BOOLEAN -> CodeBlock.of("p.getBooleanValue()");
                case BYTE -> CodeBlock.of("p.getByteValue()");
                case SHORT -> CodeBlock.of("p.getShortValue()");
                case INT -> CodeBlock.of("p.getIntValue()");
                case LONG -> CodeBlock.of("p.getLongValue()");
                case CHAR -> CodeBlock.of("p.getCharValue()");
                case FLOAT -> CodeBlock.of("p.getFloatValue()");
                case DOUBLE -> CodeBlock.of("p.getDoubleValue()");
                default -> throw new AssertionError("Encountered unknown primitive type: " + type.getKind());
            };
        } else if (TypesUtils.isString(type)) {
            return CodeBlock.of("p.getText()");
        } else if (types().isAssignable(type,
                types().erasure(elements().getTypeElement(Enum.class.getCanonicalName()).asType()))) {
            if (!deserializationConfig.isEnabled(DeserializationFeature.READ_ENUMS_USING_TO_STRING)) {
                return CodeBlock.of("$T.valueOf(p.getText())", type);
            }
        }
        return CodeBlock.of("null");
    }

    private List<VariableElement> getEnumConstants(TypeMirror type) {
        List<VariableElement> enumValues = new ArrayList<>();
        for (Element e : elements().getAllMembers((TypeElement) types().asElement(type))) {
            if (e.getKind() == ElementKind.ENUM_CONSTANT) {
                enumValues.add((VariableElement) e);
            }
        }
        return enumValues;
    }

    private JsonIgnoreProperties.Value getIgnoredProperties() {
        JsonIgnoreProperties anno = typeElement.getAnnotation(JsonIgnoreProperties.class);
        if (anno == null) {
            return JsonIgnoreProperties.Value.empty();
        }
        return JsonIgnoreProperties.Value.from(anno);
    }

    private Elements elements() {
        return processingEnv.getElementUtils();
    }

    private Types types() {
        return processingEnv.getTypeUtils();
    }
}
