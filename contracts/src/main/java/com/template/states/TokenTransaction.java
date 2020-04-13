package com.template.states;

import com.template.contracts.TokenTransactionContract;
import com.template.schemas.TokenTransactionSchemaV1;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// *********
// * State *
// *********
@BelongsToContract(TokenTransactionContract.class)
public class TokenTransaction implements LinearState, QueryableState {

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final Party explorer;
    @NotNull
    private final Instant timestamp;
    @NotNull
    /*
    * Examples of "type" can be:
    *     - ISSUE
    *     - MOVE
    *     - REDEEM
    * */
    private final String type;
    /*
    * Examples of "fromHolder" and "toHolder" can be:
    *     - Party.name: If token holder is a Party.
    *     - AccountInfo.identifier: If token holder is an account.
    * */
    private final String fromHolder;
    private final String toHolder;
    // Tokens SDK uses "Amount.quantity" of type "long", so we'll use the same.
    private final long quantity;

    public TokenTransaction(@NotNull UniqueIdentifier linearId, @NotNull Party explorer,
                            @NotNull Instant timestamp, @NotNull String type,
                            String fromHolder, String toHolder, long quantity) {
        if (quantity < 0)
            throw new IllegalStateException(
                    String.format("Quantity cannot be a negative value %d.", quantity));
        if (fromHolder.equals(toHolder))
            throw new IllegalStateException(
                    String.format("From-holder %s and to-holder %s cannot be identical.", fromHolder, toHolder));

        this.linearId = linearId;
        this.explorer = explorer;
        this.timestamp = timestamp;
        this.type = type;
        this.fromHolder = fromHolder;
        this.toHolder = toHolder;
        this.quantity = quantity;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(explorer);
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(@NotNull MappedSchema schema) {
        if (schema instanceof TokenTransactionSchemaV1) {
            return new TokenTransactionSchemaV1.PersistentTokenTransaction(
                    this.getLinearId().getId().toString(),
                    this.getExplorer().getName().toString(),
                    this.getTimestamp(),
                    this.getType(),
                    this.getFromHolder(),
                    this.getToHolder(),
                    this.getQuantity()
            );
        }
        else
            throw new IllegalArgumentException(String.format("Unrecognized schema %s", schema.toString()));
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return Collections.singletonList(new TokenTransactionSchemaV1());
    }

    @NotNull
    public Party getExplorer() {
        return explorer;
    }

    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @NotNull
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenTransaction that = (TokenTransaction) o;
        return getQuantity() == that.getQuantity() &&
                getLinearId().equals(that.getLinearId()) &&
                getExplorer().equals(that.getExplorer()) &&
                getTimestamp().equals(that.getTimestamp()) &&
                getType().equals(that.getType()) &&
                Objects.equals(getFromHolder(), that.getFromHolder()) &&
                Objects.equals(getToHolder(), that.getToHolder());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLinearId(), getExplorer(), getTimestamp(), getType(),
                getFromHolder(), getToHolder(), getQuantity());
    }
}