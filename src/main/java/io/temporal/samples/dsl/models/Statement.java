package io.temporal.samples.dsl.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Statement {
  public ActivityInvocation activity;
  public Sequence sequence;
  public Parrallel parrallel;

  @JsonCreator
  public Statement(
      @JsonProperty("sequence") Sequence sequence,
      @JsonProperty("activity") ActivityInvocation activity,
      @JsonProperty("parallel") Parrallel parrallel) {
    this.activity = activity;
    this.sequence = sequence;
    this.parrallel = parrallel;
  }

  public Void execute(Map<String, String> bindings) {
    if (this.parrallel != null) {
      this.parrallel.execute(bindings);
    }

    if (this.sequence != null) {
      this.sequence.execute(bindings);
    }

    if (this.activity != null) {
      this.activity.execute(bindings);
    }

    return null;
  }
}
