package com.template;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.template.flows.IssueTokensWithTransaction;
import com.template.schemas.TokenTransactionSchemaV1;
import com.template.states.TokenTransaction;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.FieldInfo;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria;
import net.corda.core.node.services.vault.Sort;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.getField;
import static org.junit.Assert.assertEquals;

public class TokenTransactionFlowTests {

    private MockNetwork network;
    private StartedMockNode bank;
    private StartedMockNode explorer;
    private StartedMockNode alice;
    private StartedMockNode bob;
    private Party bankParty;
    private Party explorerParty;
    private Party aliceParty;
    private Party bobParty;
    private final long quantity = 100;
    // Tokens store quantities in smallest denomination (i.e. 100 USD is stored as 10,000 cents).
    private final long quantityInSmallestDenomination = 100*100;

    public TokenTransactionFlowTests() {
    }

    @Before
    public void setup() {

        Map<String, String> tokensWorkflowsConfig = new LinkedHashMap<>();
        tokensWorkflowsConfig.put("notary", "O=Notary,L=London,C=GB");
        Map<String, String> tokensSelectionConfig = new LinkedHashMap<>();
        tokensSelectionConfig.put("stateSelection.inMemory.enabled","false");
        tokensSelectionConfig.put("stateSelection.inMemory.indexingStrategies", "[\"EXTERNAL_ID\"]");
        tokensSelectionConfig.put("stateSelection.inMemory.cacheSize", "1024");

        network = new MockNetwork(new MockNetworkParameters(ImmutableList.of(
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.template.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows").withConfig(tokensWorkflowsConfig),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.selection").withConfig(tokensSelectionConfig)
        )).withNotarySpecs(Collections.singletonList(
                new MockNetworkNotarySpec(CordaX500Name.parse("O=Notary,L=London,C=GB"), false))));

        bank = network.createPartyNode(CordaX500Name.parse("O=Bank,L=London,C=GB"));
        explorer = network.createPartyNode(CordaX500Name.parse("O=Explorer,L=London,C=GB"));
        alice = network.createPartyNode(CordaX500Name.parse("O=Alice,L=London,C=GB"));
        bob = network.createPartyNode(CordaX500Name.parse("O=Bob,L=London,C=GB"));

        bankParty = bank.getInfo().getLegalIdentities().get(0);
        explorerParty = explorer.getInfo().getLegalIdentities().get(0);
        aliceParty = alice.getInfo().getLegalIdentities().get(0);
        bobParty = bob.getInfo().getLegalIdentities().get(0);

        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void testIssueTokenWithTokenTransaction() throws ExecutionException, InterruptedException {
        IssueTokensWithTransaction.Initiator flow = new IssueTokensWithTransaction
                .Initiator(aliceParty, quantity, explorerParty);
        CordaFuture<SignedTransaction> future = bank.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();

        // Check the recorded transaction in all vaults.
        for (StartedMockNode node : Arrays.asList(bank, alice, explorer)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions()
                    .getTransaction(signedTx.getId());

            // Check recorded token.
            List<FungibleToken> tokenOutputs = recordedTx.getTx().outputsOfType(FungibleToken.class);
            assert (tokenOutputs.size() == 1);
            FungibleToken recordedToken = tokenOutputs.get(0);
            assertEquals(recordedToken.getIssuer(), bankParty);
            assertEquals(recordedToken.getHolder(), aliceParty);
            assertEquals(recordedToken.getAmount().getQuantity(), quantityInSmallestDenomination);

            // Check recorded token-transaction.
            List<TokenTransaction> tokenTransactionOutputs = recordedTx.getTx()
                    .outputsOfType(TokenTransaction.class);
            assert (tokenTransactionOutputs.size() == 1);
            TokenTransaction recordedTokenTransaction = tokenTransactionOutputs.get(0);
            assertEquals(recordedTokenTransaction.getExplorer(), explorerParty);
            assertEquals(recordedTokenTransaction.getType(), "ISSUE");
            assertEquals(recordedTokenTransaction.getFromHolder(), bankParty.getName().toString());
            assertEquals(recordedTokenTransaction.getToHolder(), aliceParty.getName().toString());
            assertEquals(recordedTokenTransaction.getQuantity(), quantity);
        }

        // Check the recorded token state in holder's vault (i.e.token's only participant).
        alice.transaction(() -> {
            List<StateAndRef<FungibleToken>> tokens = alice.getServices().getVaultService()
                    .queryBy(FungibleToken.class).getStates();
            assertEquals(1, tokens.size());
            FungibleToken recordedToken = tokens.get(0).getState().getData();
            assertEquals(recordedToken.getIssuer(), bankParty);
            assertEquals(recordedToken.getHolder(), aliceParty);
            assertEquals(recordedToken.getAmount().getQuantity(), quantityInSmallestDenomination);

            return null;
        });

        // Check the recorded token-transaction state in explorer's vault (i.e. token-transaction's only participant).
        explorer.transaction(() -> {
            List<StateAndRef<TokenTransaction>> tokenTransactions = explorer.getServices().getVaultService()
                    .queryBy(TokenTransaction.class).getStates();
            assertEquals(1, tokenTransactions.size());
            TokenTransaction recordedTokenTransaction = tokenTransactions.get(0).getState().getData();
            assertEquals(recordedTokenTransaction.getExplorer(), explorerParty);
            assertEquals(recordedTokenTransaction.getType(), "ISSUE");
            assertEquals(recordedTokenTransaction.getFromHolder(), bankParty.getName().toString());
            assertEquals(recordedTokenTransaction.getToHolder(), aliceParty.getName().toString());
            assertEquals(recordedTokenTransaction.getQuantity(), quantity);

            return null;
        });
    }

    @Test
    public void testQueries() {
        // Issue some tokens.
        IssueTokensWithTransaction.Initiator flow1 = new IssueTokensWithTransaction
                .Initiator(aliceParty, 50, explorerParty);
        bank.startFlow(flow1);
        network.runNetwork();
        IssueTokensWithTransaction.Initiator flow2 = new IssueTokensWithTransaction
                .Initiator(aliceParty, 75, explorerParty);
        bank.startFlow(flow2);
        network.runNetwork();
        IssueTokensWithTransaction.Initiator flow3 = new IssueTokensWithTransaction
                .Initiator(aliceParty, 100, explorerParty);
        bank.startFlow(flow3);
        network.runNetwork();
        IssueTokensWithTransaction.Initiator flow4 = new IssueTokensWithTransaction
                .Initiator(bobParty, 30, explorerParty);
        bank.startFlow(flow4);
        network.runNetwork();
        IssueTokensWithTransaction.Initiator flow5 = new IssueTokensWithTransaction
                .Initiator(bobParty, 60, explorerParty);
        bank.startFlow(flow5);
        network.runNetwork();

        //
        /*
        * Having a custom schema enables custom query criteria.
        * net.corda.core.node.services.vault.QueryCriteriaUtils has a lot of useful functions;
        * net.corda.node.services.vault.VaultQueryJavaTests demonstrates how to use them.
        * */
        explorer.transaction(() -> {
            try {
                // type = ISSUE.
                FieldInfo type = getField("type",
                        TokenTransactionSchemaV1.PersistentTokenTransaction.class);
                QueryCriteria typeIsIssue = new VaultCustomQueryCriteria(Builder.equal(type, "ISSUE"));

                // Average quantity by to-holder.
                FieldInfo quantity = getField("quantity",
                        TokenTransactionSchemaV1.PersistentTokenTransaction.class);
                FieldInfo toHolder = getField("toHolder",
                        TokenTransactionSchemaV1.PersistentTokenTransaction.class);
                QueryCriteria avgQtyByToHolder = new VaultCustomQueryCriteria(Builder.avg(quantity,
                        Collections.singletonList(toHolder), Sort.Direction.DESC));

                // Average issued quantity grouped by to-holder.
                QueryCriteria avgIssuedQtyByToHolder = typeIsIssue.and(avgQtyByToHolder);

                // Execute query.
                Vault.Page<TokenTransaction> tokenTransactions = explorer.getServices().getVaultService()
                        .queryBy(TokenTransaction.class, avgIssuedQtyByToHolder);

                // We issued to Alice and Bob; so we should have 2 groups.
                // Issued to Alice 50, 75, 100 -> avg = 75.
                assertEquals(tokenTransactions.getOtherResults().get(0), 75.0);
                assertEquals(tokenTransactions.getOtherResults().get(1), "O=Alice, L=London, C=GB");
                // Issued to Bob 30, 60 -> avg = 45.
                assertEquals(tokenTransactions.getOtherResults().get(2), 45.0);
                assertEquals(tokenTransactions.getOtherResults().get(3), "O=Bob, L=London, C=GB");
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        });
    }
}
