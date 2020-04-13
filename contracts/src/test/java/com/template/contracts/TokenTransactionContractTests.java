package com.template.contracts;

import com.template.states.TokenTransaction;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static net.corda.testing.node.NodeTestUtils.ledger;

public class TokenTransactionContractTests {
    static private final MockServices ledgerServices = new MockServices(
            Collections.singletonList("com.template.contracts"));
    static private final TestIdentity bank = new TestIdentity(
            new CordaX500Name("Bank", "London", "GB"));
    static private final TestIdentity explorer = new TestIdentity(
            new CordaX500Name("Explorer", "London", "GB"));
    static private final TestIdentity alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB"));
    static private final TestIdentity bob = new TestIdentity(
            new CordaX500Name("Bob", "London", "GB"));

    static private final TokenTransaction tokenTransaction = new TokenTransaction(new UniqueIdentifier(),
            explorer.getParty(), Instant.now(), "MOVE", alice.getParty().getName().toString(),
            bob.getParty().getName().toString(), 10);

    @Test
    public void transactionMustIncludeCreateCommand() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenTransactionContract.ID, tokenTransaction);
                /*
                * Adding a dummy command (in real life, it would be a token command since we'll always
                * combine TokenContractState with token states).
                **/
                tx.command(Collections.singletonList(alice.getPublicKey()), new DummyContract.Commands.Move());
                tx.failsWith("There should be only one command.");
                tx.command(Collections.singletonList(explorer.getPublicKey()),
                        new TokenTransactionContract.Commands.Create());
                tx.verifies();
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionMustHaveNoInputs() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenTransactionContract.ID, tokenTransaction);
                tx.output(TokenTransactionContract.ID, tokenTransaction);
                tx.command(Collections.singletonList(explorer.getPublicKey()),
                        new TokenTransactionContract.Commands.Create());
                tx.failsWith("There should be no inputs.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionMustHaveOutputs() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                /*
                 * A transaction must have at least one input or output state;
                 * so we're adding a dummy one.
                 **/
                tx.input(TokenTransactionContract.ID, new DummyState(0));
                tx.command(Collections.singletonList(explorer.getPublicKey()),
                        new TokenTransactionContract.Commands.Create());
                tx.failsWith("There should be outputs.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void explorerMustSignTransaction() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenTransactionContract.ID, tokenTransaction);
                tx.command(Collections.singletonList(alice.getPublicKey()),
                        new TokenTransactionContract.Commands.Create());
                tx.failsWith("Explorer is a required signer.");
                return null;
            });
            return null;
        }));
    }
}