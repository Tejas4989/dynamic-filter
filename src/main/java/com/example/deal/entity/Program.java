package com.example.deal.entity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Program domain entity.
 * 
 * <p>Represents a program that belongs to a deal (One-to-Many relationship).
 * This is a simple domain object - filtering is done via DealFilterView.</p>
 *
 * @param programId unique program identifier
 * @param programName the program name
 * @param programType the type of program (RESEARCH, DEVELOPMENT, TESTING, etc.)
 * @param budget the program budget
 */
public record Program(
    Long programId,
    String programName,
    String programType,
    BigDecimal budget
) {
    public Program {
        Objects.requireNonNull(programName, "programName cannot be null");
    }
    
    /**
     * Builder for convenient Program construction.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Long programId;
        private String programName;
        private String programType;
        private BigDecimal budget;
        
        private Builder() {}
        
        public Builder programId(Long programId) {
            this.programId = programId;
            return this;
        }
        
        public Builder programName(String programName) {
            this.programName = programName;
            return this;
        }
        
        public Builder programType(String programType) {
            this.programType = programType;
            return this;
        }
        
        public Builder budget(BigDecimal budget) {
            this.budget = budget;
            return this;
        }
        
        public Program build() {
            return new Program(programId, programName, programType, budget);
        }
    }
}
