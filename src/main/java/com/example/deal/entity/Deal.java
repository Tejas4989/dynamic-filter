package com.example.deal.entity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Deal domain entity.
 * 
 * <p>Rich domain object containing:
 * <ul>
 *   <li>Direct fields from deals table</li>
 *   <li>Denormalized analyst info from users table (via FK)</li>
 *   <li>Nested collection of programs (One-to-Many)</li>
 * </ul>
 * 
 * <p>This is the domain entity returned by the API. Filtering is done via
 * the flattened {@link DealFilterView} query entity.</p>
 *
 * @param dealId unique deal identifier
 * @param dealName the deal name
 * @param userOption the analyst assigned to this deal (contains analystId and analystName)
 * @param dealStatus the deal status (DRAFT, ACTIVE, PENDING, CLOSED)
 * @param dealAmount the total deal amount
 * @param programs list of programs associated with this deal
 */
public record Deal(
    Long dealId,
    String dealName,
    UserOption analyst,
    String dealStatus,
    BigDecimal dealAmount,
    List<Program> programs
) {
    public Deal {
        Objects.requireNonNull(dealName, "dealName cannot be null");
        programs = programs != null ? List.copyOf(programs) : List.of();
    }
    
    /**
     * Builder for convenient Deal construction.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Long dealId;
        private String dealName;
        private UserOption analyst;
        private String dealStatus;
        private BigDecimal dealAmount;
        private List<Program> programs = List.of();
        
        private Builder() {}
        
        public Builder dealId(Long dealId) {
            this.dealId = dealId;
            return this;
        }
        
        public Builder dealName(String dealName) {
            this.dealName = dealName;
            return this;
        }

        public Builder analyst(UserOption analyst) {
            this.analyst = analyst;
            return this;
        }
        
        public Builder dealStatus(String dealStatus) {
            this.dealStatus = dealStatus;
            return this;
        }
        
        public Builder dealAmount(BigDecimal dealAmount) {
            this.dealAmount = dealAmount;
            return this;
        }
        
        public Builder programs(List<Program> programs) {
            this.programs = programs;
            return this;
        }
        
        public Deal build() {
            return new Deal(dealId, dealName, analyst, dealStatus, dealAmount, programs);
        }
    }
}
