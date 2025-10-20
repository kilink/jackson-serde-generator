package net.kilink.jackson;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AutoService(Processor.class)
public final class AnnotationProcessor extends AbstractProcessor {

    private static final String DEFAULT_PACKAGE_NAME = "net.kilink.jackson";
    private static final String DEFAULT_MODULE_NAME = "GeneratedModule";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<ClassName> generatedSerializers = new ArrayList<>();
        List<DeserializerInfo> generatedDeserializers = new ArrayList<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(AutoSerde.class)) {
            if (!element.getKind().isClass()) {
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            AutoSerde anno = typeElement.getAnnotation(AutoSerde.class);

            if (anno.value() == AutoSerde.AutoGenerate.SERIALIZER || anno.value() == AutoSerde.AutoGenerate.BOTH) {
                SerializerGenerator generator = new SerializerGenerator(typeElement, processingEnv);
                JavaFile sourceFile = generator.generate();
                try {
                    sourceFile.writeTo(processingEnv.getFiler());
                    generatedSerializers.add(ClassName.get(sourceFile.packageName, sourceFile.typeSpec.name));
                } catch (IOException exc) {
                    messager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Encountered error while attempting to write file: " + exc.getLocalizedMessage(),
                            element);
                }
            }

            if (anno.value() == AutoSerde.AutoGenerate.DESERIALIZER || anno.value() == AutoSerde.AutoGenerate.BOTH) {
                DeserializerGenerator generator = new DeserializerGenerator(typeElement, processingEnv);
                JavaFile sourceFile = generator.generate();
                try {
                    sourceFile.writeTo(processingEnv.getFiler());
                    generatedDeserializers.add(new DeserializerInfo(
                            ClassName.get(typeElement),
                            ClassName.get(sourceFile.packageName, sourceFile.typeSpec.name)));
                } catch (IOException exc) {
                    messager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Encountered error while attempting to write file: " + exc.getLocalizedMessage(),
                            element);
                }
            }
        }

        if (generatedSerializers.isEmpty() && generatedDeserializers.isEmpty()) {
            return false;
        }

        ModuleGenerator.Builder moduleGenerator = ModuleGenerator.builder()
                .withModuleName(ClassName.get(DEFAULT_PACKAGE_NAME, DEFAULT_MODULE_NAME))
                .withSerializers(generatedSerializers);
        for (DeserializerInfo info : generatedDeserializers) {
            moduleGenerator.withDeserializer(info.className(), info.deserializer());
        }
        JavaFile sourceFile = moduleGenerator.build().generate();
        try {
            sourceFile.writeTo(processingEnv.getFiler());
        } catch (IOException exc) {
            messager().printMessage(
                    Diagnostic.Kind.ERROR, "Encountered error while attempting to write file: " + exc.getLocalizedMessage());
        }

        return false;
    }

    private Messager messager() {
        return processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(AutoSerde.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private record DeserializerInfo(ClassName className, ClassName deserializer) {}
}
