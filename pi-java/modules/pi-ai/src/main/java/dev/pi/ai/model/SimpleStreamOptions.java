package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class SimpleStreamOptions extends StreamOptions {
    private final ThinkingLevel reasoning;
    private final ThinkingBudgets thinkingBudgets;

    private SimpleStreamOptions(Builder builder) {
        super(builder);
        this.reasoning = builder.reasoning;
        this.thinkingBudgets = builder.thinkingBudgets;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ThinkingLevel reasoning() {
        return reasoning;
    }

    public ThinkingBudgets thinkingBudgets() {
        return thinkingBudgets;
    }

    public static final class Builder extends StreamOptions.Builder<Builder> {
        private ThinkingLevel reasoning;
        private ThinkingBudgets thinkingBudgets;

        public Builder reasoning(ThinkingLevel reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) {
            this.thinkingBudgets = thinkingBudgets;
            return this;
        }

        @Override
        public SimpleStreamOptions build() {
            return new SimpleStreamOptions(this);
        }
    }
}

