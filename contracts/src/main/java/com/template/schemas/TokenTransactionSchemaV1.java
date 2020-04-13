package com.template.schemas;

import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Collections;

public class TokenTransactionSchemaV1 extends MappedSchema {

    public TokenTransactionSchemaV1() {
        super(TokenTransactionSchema.class, 1, Collections.singletonList(PersistentTokenTransaction.class));
    }

    @Entity
    @Table(name = "token_transactions")
    public static class PersistentTokenTransaction extends PersistentState {
        @Column(name = "linear_id") private final String linearId;
        @Column(name = "explorer") private final String explorer;
        @Column(name = "timestamp") private final Instant timestamp;
        @Column(name = "type") private final String type;
        @Column(name = "from_holder") private final String fromHolder;
        @Column(name = "to_holder") private final String toHolder;
        @Column(name = "quantity") private final long quantity;


        public PersistentTokenTransaction(String linearId, String explorer, Instant timestamp, String type,
                                          String fromHolder, String toHolder, long quantity) {
            this.linearId = linearId;
            this.explorer = explorer;
            this.timestamp = timestamp;
            this.type = type;
            this.fromHolder = fromHolder;
            this.toHolder = toHolder;
            this.quantity = quantity;
        }

        // Default constructor required by Hibernate.
        public PersistentTokenTransaction() {
            this.linearId = null;
            this.explorer = null;
            this.timestamp = null;
            this.type = null;
            this.fromHolder = null;
            this.toHolder = null;
            this.quantity = 0;
        }

        public String getLinearId() {
            return linearId;
        }

        public String getExplorer() {
            return explorer;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getType() {
            return type;
        }

        public String getFromHolder() {
            return fromHolder;
        }

        public String getToHolder() {
            return toHolder;
        }

        public long getQuantity() {
            return quantity;
        }
    }
}
