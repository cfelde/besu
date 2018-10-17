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
package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

public class CliqueDifficultyCalculator implements DifficultyCalculator<CliqueContext> {

  private final Address localAddress;

  private final BigInteger IN_TURN_DIFFICULTY = BigInteger.valueOf(2);
  private final BigInteger OUT_OF_TURN_DIFFICULTY = BigInteger.ONE;

  public CliqueDifficultyCalculator(final Address localAddress) {
    this.localAddress = localAddress;
  }

  @Override
  public BigInteger nextDifficulty(
      final long time, final BlockHeader parent, final ProtocolContext<CliqueContext> context) {

    final Address nextProposer =
        CliqueHelpers.getProposerForBlockAfter(
            parent, context.getConsensusState().getVoteTallyCache());
    return nextProposer.equals(localAddress) ? IN_TURN_DIFFICULTY : OUT_OF_TURN_DIFFICULTY;
  }
}
