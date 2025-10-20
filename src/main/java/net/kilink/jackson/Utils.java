package net.kilink.jackson;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.util.BeanUtil;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

final class Utils {

    private Utils() {}

    static String nameForGetter(PropertyAccessor accessorType, Element element) {
        return switch (accessorType) {
            case GETTER -> BeanUtil.stdManglePropertyName(element.getSimpleName().toString(), 3);
            case IS_GETTER -> BeanUtil.stdManglePropertyName(element.getSimpleName().toString(), 2);
            default -> throw new IllegalArgumentException("Unsupported property accessor type: " + accessorType);
        };
    }

    static String nameForSetter(ExecutableElement element) {
        return BeanUtil.stdManglePropertyName(element.getSimpleName().toString(), 3);
    }
}
