# AutoSerde
Code generator for Jackson Serializers / Deserializers.

## Why?

- Avoid runtime reflection / instrospection at runtime. Do the work up front at compile time so we don't pay the cost at runtime.
  In general, reflection can be very slow and comes with no compile-time type safety.
  
## Goals / Non-Goals

- Support basic serialization / deserialization features of Jackson, such as annotation-based configuration overrides (think JsonProperty, JsonNaming, etc).
- It is a non-goal to support every single Jackson feature, particularly the more dynamic ones. The plan is to support an explicit subset of features.
- Basic Kotlin support; we should be able to support in particular data classes.

## Example

Given a typical POJO like the following:

```java
@AutoSerde
public class Foo {
  private String name;
  private int value;
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }
  
  public void setValue(int value) {
    this.value = value;
  }
  
  public int getValue() {
    return value;
  }
}
```

After running the AutoSerde annotation processor, we would end up with the following generated Serializer:

```java
public class FooSerializer extends StdSerializer<Foo> {
  public FooSerializer() {
    super(Foo.class);
  }

  @Override
  public void serialize(Foo value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    gen.writeStartObject(value);
    gen.writeFieldName("name");
    if (value.getName() == null) {
      gen.writeNull();
    } else {
      gen.writeString(value.getName());
    }
    gen.writeFieldName("value");
    gen.writeNumber(value.getValue());
    gen.writeEndObject();
  }
}
```
And Deserializer:

```java
public class FooDeserializer extends StdDeserializer<Foo> {
  private static final boolean ignoreUnknown = true;
  private static final Set<String> ignored = Collections.emptySet();

  public FooDeserializer() {
    super(Fooclass);
  }

  @Override
  public Foo deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    if (!p.isExpectedStartObjectToken()) {
      ctxt.handleUnexpectedToken(getValueType(ctxt), p);
    }

    Foo obj = new Foo();
    JsonToken token = p.nextToken();
    while (token != null) {
      if (token == JsonToken.FIELD_NAME) {
        p.nextValue();
        String fieldName = p.currentName();
        switch (fieldName) {
          case "name":
            obj.setName(p.getText());
            break;
          case "value":
            obj.setValue(p.getIntValue());
            break;
          default:
            if (!(ignoreUnknown || ignored.contains(fieldName))) {
              handleUnknownProperty(p, ctxt, Pojo.class, fieldName);
            }
          }
        }
        token = p.nextToken();
      }
      return obj;
    }
  }
```
As well as a Jackson Module for registering the generated classes, which is discoverable via Jackson's SPI support:


```java
public class GeneratedModule extends SimpleModule {
  {
    addSerializer(new FooSerializer());
    addDeserializer(Foo.class, new FooDeserializer());
  }
}

```
