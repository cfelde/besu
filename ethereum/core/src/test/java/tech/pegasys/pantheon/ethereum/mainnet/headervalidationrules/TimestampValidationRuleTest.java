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
package tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;

import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TimestampValidationRuleTest {

  @Test
  public void headerTimestampSufficientlyFarIntoFutureVadidatesSuccessfully() {
    final TimestampValidationRule uut = new TimestampValidationRule(0, 10);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    // Note: This is 10 seconds after Unix epoch (i.e. long way in the past.)
    headerBuilder.timestamp(10);
    final BlockHeader parent = headerBuilder.buildHeader();

    headerBuilder.timestamp(parent.getTimestamp() + 11);
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isTrue();
  }

  @Test
  public void headerTimestampDifferenceMustBePositive() {
    Assertions.assertThatThrownBy(() -> new TimestampValidationRule(0, -1))
        .hasMessage("minimumSecondsSinceParent must be positive");
  }

  @Test
  public void headerTimestampTooCloseToParentFailsValidation() {
    final TimestampValidationRule uut = new TimestampValidationRule(0, 10);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    // Note: This is 10 seconds after Unix epoch (i.e. long way in the past.)
    headerBuilder.timestamp(10);
    final BlockHeader parent = headerBuilder.buildHeader();

    headerBuilder.timestamp(parent.getTimestamp() + 1);
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isFalse();
  }

  @Test
  public void headerTimestampIsBehindParentFailsValidation() {
    final TimestampValidationRule uut = new TimestampValidationRule(0, 10);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    // Note: This is 100 seconds after Unix epoch (i.e. long way in the past.)
    headerBuilder.timestamp(100);
    final BlockHeader parent = headerBuilder.buildHeader();

    headerBuilder.timestamp(parent.getTimestamp() - 11);
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isFalse();
  }

  @Test
  public void headerNewerThanCurrentSystemFailsValidation() {
    final long acceptableClockDrift = 5;
    final TimestampValidationRule uut = new TimestampValidationRule(acceptableClockDrift, 10);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    // Create Parent Header @ 'now'
    headerBuilder.timestamp(
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    final BlockHeader parent = headerBuilder.buildHeader();

    // Create header for validation with a timestamp in the future (1 second too far away)
    headerBuilder.timestamp(parent.getTimestamp() + acceptableClockDrift + 1);
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isFalse();
  }

  @Test
  public void futureHeadersAreValidIfTimestampWithinTolerance() {
    final long acceptableClockDrift = 5;
    final TimestampValidationRule uut = new TimestampValidationRule(acceptableClockDrift, 10);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    // Create Parent Header @ 'now'
    headerBuilder.timestamp(
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    final BlockHeader parent = headerBuilder.buildHeader();

    // Create header for validation with a timestamp in the future (1 second too far away)
    // (-1) to prevent spurious failures
    headerBuilder.timestamp(parent.getTimestamp() + acceptableClockDrift - 1);
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isFalse();
  }
}
