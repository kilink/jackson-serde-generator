package net.kilink.jackson;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@AutoSerde
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
@JsonIgnoreProperties(value = {"baz"}, ignoreUnknown = true)
public final class Pojo {

    public enum Color { @JsonProperty("Red") RED, GREEN, BLUE }

    private String prop1;
    private Integer prop2;
    private boolean active = false;
    private Color color;

    public Pojo() {}

    @JsonProperty("renamedProp")
    public String getProp1() {
      return prop1;
    }

    public void setProp1(String prop1) {
      this.prop1 = prop1;
    }

    public Integer getProp2() {
      return prop2;
    }

    public void setProp2(Integer prop2) {
      this.prop2 = prop2;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Color getColor() {
      return color;
    }

    public void setColor(Color color) {
      this.color = color;
    }

    public Set<String> getListOfThings() {
      return Collections.emptySet();
    }

    public HashMap<String, String> getOtherThings() {
      return new HashMap<>();
    }

    Byte[] getBinaryData() {
      return new Byte[5];
    }

    private Object getSomething() {
      throw new IllegalStateException("Private getter shouldn't be called");
    }
}