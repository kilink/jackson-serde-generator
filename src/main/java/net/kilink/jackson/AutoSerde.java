package net.kilink.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AutoSerde {
    AutoGenerate value() default AutoGenerate.BOTH;
    String packageName() default "";
    String serializerName() default "";
    String deserializerName() default "";

    enum AutoGenerate { SERIALIZER, DESERIALIZER, BOTH }
}
