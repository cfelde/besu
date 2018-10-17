/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions.TransactionSelectionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import com.google.common.collect.Lists;
import org.junit.Test;

public class PendingTransactionsTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final KeyPair KEYS1 = KeyPair.generate();
  private static final KeyPair KEYS2 = KeyPair.generate();
  private final PendingTransactions transactions = new PendingTransactions(MAX_TRANSACTIONS);
  private final Transaction transaction1 = createTransaction(2);
  private final Transaction transaction2 = createTransaction(1);

  private final PendingTransactionListener listener = mock(PendingTransactionListener.class);
  private static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
  private static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());

  @Test
  public void shouldAddATransaction() {
    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);

    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(2);
  }

  @Test
  public void shouldReturnEmptyOptionalWhenNoTransactionWithGivenHashExists() {
    assertThat(transactions.getTransactionByHash(Hash.EMPTY_TRIE_HASH)).isEmpty();
  }

  @Test
  public void shouldGetTransactionByHash() {
    transactions.addRemoteTransaction(transaction1);
    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldDropOldestTransactionWhenLimitExceeded() {
    final Transaction oldestTransaction = createTransaction(0);
    transactions.addRemoteTransaction(oldestTransaction);
    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1));
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(oldestTransaction);
  }

  @Test
  public void shouldHandleMaximumTransactionLimitCorrectlyWhenSameTransactionAddedMultipleTimes() {
    transactions.addRemoteTransaction(createTransaction(0));
    transactions.addRemoteTransaction(createTransaction(0));

    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1));
    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 2));
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
  }

  @Test
  public void shouldPrioritizeLocalTransaction() {
    final Transaction localTransaction = createTransaction(0);
    transactions.addLocalTransaction(localTransaction);

    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionPending(localTransaction);
  }

  @Test
  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
    final Transaction firstLocalTransaction = createTransaction(0);
    transactions.addLocalTransaction(firstLocalTransaction);

    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
      transactions.addLocalTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(firstLocalTransaction);
  }

  @Test
  public void shouldNotifyListenerWhenRemoteTransactionAdded() {
    transactions.addTransactionListener(listener);

    transactions.addRemoteTransaction(transaction1);

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void shouldNotifyListenerWhenLocalTransactionAdded() {
    transactions.addTransactionListener(listener);

    transactions.addLocalTransaction(transaction1);

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void selectTransactionsUntilSelectorRequestsNoMore() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.COMPLETE_OPERATION;
        });

    assertThat(parsedTransactions.size()).isEqualTo(1);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
  }

  @Test
  public void selectTransactionsUntilPendingIsEmpty() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
  }

  @Test
  public void invalidTransactionIsDeletedFromPendingTransactions() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);

    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldReturnEmptyOptionalAsMaximumNonceWhenNoTransactionsPresent() {
    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void shouldReturnEmptyOptionalAsMaximumNonceWhenLastTransactionForSenderRemoved() {
    final Transaction transaction = transactionWithNonceAndSender(1, KEYS1);
    transactions.addRemoteTransaction(transaction);
    transactions.removeTransaction(transaction);
    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void shouldReplaceTransactionWithSameSenderAndNonce() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    final Transaction transaction2 = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction2)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction1b)).isTrue();

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction1b);
    assertTransactionPending(transaction2);
    assertThat(transactions.size()).isEqualTo(2);
  }

  @Test
  public void shouldReplaceOnlyTransactionFromSenderWhenItHasTheSameNonce() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction1b)).isTrue();

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction1b);
    assertThat(transactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldNotReplaceTransactionWithSameSenderAndNonceWhenGasPriceIsLower() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();

    transactions.addTransactionListener(listener);
    assertThat(transactions.addRemoteTransaction(transaction1b)).isFalse();

    assertTransactionNotPending(transaction1b);
    assertTransactionPending(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    verifyZeroInteractions(listener);
  }

  @Test
  public void shouldTrackMaximumNonceForEachSender() {
    transactions.addRemoteTransaction(transactionWithNonceAndSender(0, KEYS1));
    assertMaximumNonceForSender(SENDER1, 1);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(1, KEYS1));
    assertMaximumNonceForSender(SENDER1, 2);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(2, KEYS1));
    assertMaximumNonceForSender(SENDER1, 3);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(20, KEYS2));
    assertMaximumNonceForSender(SENDER2, 21);
    assertMaximumNonceForSender(SENDER1, 3);
  }

  @Test
  public void shouldIterateTransactionsFromSameSenderInNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction2);
    transactions.addLocalTransaction(transaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transaction1, transaction2, transaction3);
  }

  @Test
  public void shouldNotForceNonceOrderWhenSendersDiffer() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS2);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction2);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transaction2, transaction1);
  }

  @Test
  public void shouldNotIncreasePriorityOfTransactionsBecauseOfNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);
    final Transaction transaction4 = transactionWithNonceAndSender(5, KEYS2);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction4);
    transactions.addLocalTransaction(transaction2);
    transactions.addLocalTransaction(transaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    // Ignoring nonces, the order would be 3, 2, 4, 1 but we have to delay 3 and 2 until after 1.
    assertThat(iterationOrder)
        .containsExactly(transaction4, transaction1, transaction2, transaction3);
  }

  private void assertMaximumNonceForSender(final Address sender1, final int i) {
    assertThat(transactions.getNextNonceForSender(sender1)).isEqualTo(OptionalLong.of(i));
  }

  private Transaction transactionWithNonceAndSender(final int nonce, final KeyPair keyPair) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(keyPair);
  }

  private Transaction transactionWithNonceSenderAndGasPrice(
      final int nonce, final KeyPair keyPair, final long gasPrice) {
    return new TransactionTestFixture()
        .nonce(nonce)
        .gasPrice(Wei.of(gasPrice))
        .createTransaction(keyPair);
  }

  private void assertTransactionPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.hash())).contains(t);
  }

  private void assertTransactionNotPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.hash())).isEmpty();
  }

  private Transaction createTransaction(final int transactionNumber) {
    return new TransactionTestFixture()
        .value(Wei.of(transactionNumber))
        .nonce(transactionNumber)
        .createTransaction(KEYS1);
  }
}
