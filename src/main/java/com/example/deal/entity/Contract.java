package com.example.deal.entity;

import java.util.Objects;

/**
 * Contract domain entity.
 *
 * <p>Represents a contract that belongs to a program (One-to-Many relationship).
 * This is a simple domain object - filtering is done via DealFilterView.</p>
 *
 * @param contractId   unique contract identifier
 * @param contractName the contract name
 */
public record Contract(
    Long contractId,
    String contractName
) {
    public Contract {
        Objects.requireNonNull(contractName, "contractName cannot be null");
    }

    /**
     * Builder for convenient Contract construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long contractId;
        private String contractName;

        private Builder() {}

        public Builder contractId(Long contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder contractName(String contractName) {
            this.contractName = contractName;
            return this;
        }

        public Contract build() {
            return new Contract(contractId, contractName);
        }
    }
}
