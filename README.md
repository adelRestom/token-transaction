# Token-Transaction CorDapp

This example shows how to elevate the Tokens SDK with custom **on-ledger** reporting.  
A `TokenTransaction` state is combined with the token in one transaction, giving the following benefits:  

- The reporting data (i.e. `TokenTransaction`) is a **state**; making it immutable, auditable, and cryptographically secured.  
- Adding the reporting data happens in an atomic way while adding the tokens data; eliminating any discrepancy between the two.  

To see how the Token-Transaction CorDapp works, explore the flow tests under `workflows\src\test`.  
A detailed explanation of the CorDapp can be found [here]().