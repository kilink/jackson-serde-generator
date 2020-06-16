package net.kilink.jackson;

import com.google.common.io.ByteStreams;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TestUtils {
    private static class FileObjectClassLoader extends ClassLoader {
        private <T> Class<T> defineClassFromFileObject(JavaFileObject classFile) {
            final byte[] bytes;
            try (InputStream is = classFile.openInputStream()) {
                bytes = ByteStreams.toByteArray(is);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }

            @SuppressWarnings("unchecked")
            Class<T> klazz = (Class<T>) defineClass(null, bytes, 0, bytes.length);

            return klazz;
        }
    }

    private static final FileObjectClassLoader classLoader = new FileObjectClassLoader();

    public static <T> Class<T> loadClass(JavaFileObject classFile) {
        checkNotNull(classFile, "classFile = null");
        checkArgument(classFile.getKind() == JavaFileObject.Kind.CLASS);
        return classLoader.defineClassFromFileObject(classFile);
    }

    public static <T> Class<T> loadClass(String className) {
        checkNotNull(className, "className == null");
        try {
            @SuppressWarnings("unchecked")
            Class<T> klazz = (Class<T>) classLoader.loadClass(className);
            return klazz;
        } catch (ClassNotFoundException exc) {
            throw new RuntimeException(exc);
        }
    }
}
