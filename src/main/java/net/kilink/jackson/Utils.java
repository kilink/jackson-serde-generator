package net.kilink.jackson;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.util.BeanUtil;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Utils {
    @SafeVarargs
    static <T> List<T> immutableListOf(T ...elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    @SafeVarargs
    static <T> Set<T> immutableSetOf(T ...elements) {
        Set<T> set = new LinkedHashSet<>();
        Collections.addAll(set, elements);
        return Collections.unmodifiableSet(set);
    }

    @Nullable
    static String nameForGetter(PropertyAccessor accessorType, Element element) {
        if (accessorType == PropertyAccessor.GETTER) {
            return MyBeanUtil.stdPropertyName(element.getSimpleName().toString(), 3);
        } else if (accessorType == PropertyAccessor.IS_GETTER) {
            return MyBeanUtil.stdPropertyName(element.getSimpleName().toString(), 2);
        }
        throw new IllegalArgumentException();
    }

    static String nameForSetter(ExecutableElement element) {
        return MyBeanUtil.stdPropertyName(element.getSimpleName().toString(), 3);
    }

    private static class MyBeanUtil extends BeanUtil {
        private static String stdPropertyName(String baseName, int offset) {
            return BeanUtil.stdManglePropertyName(baseName, offset);
        }
    }
}
