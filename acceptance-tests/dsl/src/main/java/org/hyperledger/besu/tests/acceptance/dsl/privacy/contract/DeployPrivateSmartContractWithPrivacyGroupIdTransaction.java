/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.contract;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.lang.reflect.Method;
import java.math.BigInteger;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.tx.Contract;
import org.web3j.tx.PantheonPrivateTransactionManager;
import org.web3j.tx.PrivateTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.PantheonPrivacyGasProvider;
import org.web3j.utils.Base64String;

public class DeployPrivateSmartContractWithPrivacyGroupIdTransaction<T extends Contract>
    implements Transaction<T> {

  private static final PantheonPrivacyGasProvider GAS_PROVIDER =
      new PantheonPrivacyGasProvider(BigInteger.valueOf(1000));
  private static final Object METHOD_IS_STATIC = null;

  private final Class<T> clazz;
  private final Credentials senderCredentials;
  private final long chainId;
  private final Base64String privateFrom;
  private final Base64String privacyGroupId;

  public DeployPrivateSmartContractWithPrivacyGroupIdTransaction(
      final Class<T> clazz,
      final String transactionSigningKey,
      final long chainId,
      final String privateFrom,
      final String privacyGroupId) {
    this.clazz = clazz;
    this.senderCredentials = Credentials.create(transactionSigningKey);
    this.chainId = chainId;
    this.privateFrom = Base64String.wrap(privateFrom);
    this.privacyGroupId = Base64String.wrap(privacyGroupId);
  }

  @Override
  public T execute(final NodeRequests node) {
    final PrivateTransactionManager privateTransactionManager =
        new PantheonPrivateTransactionManager(
            node.privacy().getBesuClient(),
            GAS_PROVIDER,
            senderCredentials,
            chainId,
            privateFrom,
            privacyGroupId);
    try {
      final Method method =
          clazz.getMethod(
              "deploy", Web3j.class, TransactionManager.class, ContractGasProvider.class);

      final Object invoked =
          method.invoke(
              METHOD_IS_STATIC,
              node.privacy().getBesuClient(),
              privateTransactionManager,
              GAS_PROVIDER);

      return cast(invoked).send();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private RemoteCall<T> cast(final Object invokedMethod) {
    return (RemoteCall<T>) invokedMethod;
  }
}
