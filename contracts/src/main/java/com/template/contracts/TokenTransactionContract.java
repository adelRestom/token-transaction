package com.template.contracts;

import com.template.states.TokenTransaction;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.core.contracts.ContractsDSL.select;

// ************
// * Contract *
// ************
public class TokenTransactionContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.template.contracts.TokenTransactionContract";

    @Override
    public void verify(LedgerTransaction tx) {
        /*
        * Token-transaction state will always be part of a transaction with other types of states;
        * meaning there will be commands, inputs, and outputs that are not related to it.
        * The contract will only verify the transaction components that are relevant.
        * */
        requireThat(require -> {
            // Transaction shape constraints.
            final List<CommandWithParties<Commands.Create>> tokenTransactionCreateCommands = select(tx.getCommands(),
                    Commands.Create.class, Collections.emptyList(), Collections.emptyList());
            require.using("There should be only one command.", tokenTransactionCreateCommands.size() == 1);

            final List<TokenTransaction> tokenTransactionInputs = tx.inputsOfType(TokenTransaction.class);
            require.using("There should be no inputs.", tokenTransactionInputs.isEmpty());

            final List<TokenTransaction> tokenTransactionOutputs = tx.outputsOfType(TokenTransaction.class);
            require.using("There should be outputs.", !tokenTransactionOutputs.isEmpty());

            // Transaction signature constraints.
            final CommandWithParties<Commands.Create> createCommand = tokenTransactionCreateCommands.get(0);
            require.using("Explorer is a required signer.", createCommand.getSigners()
                    .containsAll(tokenTransactionOutputs.stream()
                            .map(it ->it.getExplorer().getOwningKey())
                            .collect(Collectors.toList())));

            return null;
        });
    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Create implements Commands {}
    }
}