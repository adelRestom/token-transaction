package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.contracts.TokenTransactionContract;
import com.template.states.TokenTransaction;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensUtilitiesKt.addIssueTokens;
import static com.r3.corda.lib.tokens.workflows.utilities.FlowUtilitiesKt.addTokenTypeJar;
import static com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilitiesKt.firstNotary;
import static com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilitiesKt.getPreferredNotary;


public class IssueTokensWithTransaction {

    @CordaSerializable
    enum CounterPartyRole {PARTICIPANT, SIGNER}

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final Party holder;
        private final long quantity;
        private final Party explorer;

        // For simplicity of example, this flow issues one token to one holder.
        public Initiator(Party holder, long quantity, Party explorer) {
            this.holder = holder;
            this.quantity = quantity;
            this.explorer = explorer;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Get preferred notary from tokens-workflows CorDapp configuration file.
            Party notary = getPreferredNotary(getServiceHub(), firstNotary());

            // Create token.
            TokenType usdType = FiatCurrency.Companion.getInstance("USD");
            IssuedTokenType issuedUsd = new IssuedTokenType(getOurIdentity(), usdType);
            Amount<IssuedTokenType> tokenAmount = AmountUtilitiesKt.amount(quantity, issuedUsd);
            FungibleToken usdToken = new FungibleToken(tokenAmount, holder, null);

            // Create token-transaction.
            TokenTransaction tokenTransaction = new TokenTransaction(new UniqueIdentifier(), explorer,
                    Instant.now(), "ISSUE", getOurIdentity().getName().toString(),
                    holder.getName().toString(), quantity);

            // Assemble transaction.
            TransactionBuilder txBuilder = new TransactionBuilder(notary);
            // Add token.
            addIssueTokens(txBuilder, Collections.singletonList(usdToken));
            addTokenTypeJar(Collections.singletonList(usdToken), txBuilder);
            // Add token-transaction.
            Command<TokenTransactionContract.Commands.Create> createTokenTransaction =
                    new Command<>(new TokenTransactionContract.Commands.Create(),
                            Collections.singletonList(tokenTransaction.getExplorer().getOwningKey()));
            txBuilder.addCommand(createTokenTransaction).addOutputState(tokenTransaction);

            // Verify transaction.
            txBuilder.verify(getServiceHub());

            // Sign locally.
            SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Collect signature from token-transaction explorer.
            FlowSession explorerSession = initiateFlow(explorer);
            explorerSession.send(CounterPartyRole.SIGNER);
            FlowSession holderSession = initiateFlow(holder);
            holderSession.send(CounterPartyRole.PARTICIPANT);
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx,
                    Collections.singletonList(explorerSession)));

            // Finalize transaction.
            return subFlow(new FinalityFlow(fullySignedTx, Arrays.asList(holderSession, explorerSession)));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Responder extends FlowLogic<SignedTransaction> {

        private final FlowSession counterPartySession;

        public Responder(FlowSession counterPartySession) {
            this.counterPartySession = counterPartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SecureHash txId = null;
            CounterPartyRole role = counterPartySession.receive(CounterPartyRole.class)
                    .unwrap(it -> it);
            /*
            * Both token holder and token-transaction explorer need to finalize the transaction,
            * but only explorer need to sign (holder is not required to sign on issuing of tokens).
            * */
            if (role == CounterPartyRole.SIGNER) {
                class SignTxFlow extends SignTransactionFlow {
                    private SignTxFlow(FlowSession otherPartyFlow) {
                        super(otherPartyFlow);
                    }

                    @Override
                    protected void checkTransaction(SignedTransaction stx) {
                        // Some validation rules.
                    }
                }
                final SignTxFlow signTxFlow = new SignTxFlow(counterPartySession);
                txId = subFlow(signTxFlow).getId();
            }

            return subFlow(new ReceiveFinalityFlow(counterPartySession, txId));
        }
    }
}
