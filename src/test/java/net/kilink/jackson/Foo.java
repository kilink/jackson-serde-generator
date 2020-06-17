package net.kilink.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class Foo {

    public enum State {
        @JsonProperty("In Progress")
        IN_PROGRESS,
        @JsonProperty("Finished")
        FINISHED,
        @JsonProperty("Failed")
        FAILED
    }

    private String bar;
    private int baz;
    private List<String> things;
    private Map<String, String> otherThings;
    private boolean enabled = false;
    private State state = null;

    public Foo() { }

    @JsonProperty("renamedBar")
    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }

    public int getBaz() {
        return baz;
    }

    public void setBaz(int baz) {
        this.baz = baz;
    }

    public List<String> getThings() {
        return things;
    }

    public void setThings(List<String> things) {
        this.things = things;
    }

    public Map<String, String> getOtherThings() {
        return otherThings;
    }

    public void setOtherThings(Map<String, String> otherThings) {
        this.otherThings = otherThings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "Foo{" +
                "bar='" + bar + '\'' +
                ", baz=" + baz +
                ", things=" + things +
                ", otherThings=" + otherThings +
                ", enabled=" + enabled +
                ", state=" + state +
                '}';
    }
}