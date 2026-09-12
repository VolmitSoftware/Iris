/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.world.pregen;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleHeapPressureTest {
    private static final MantleHeapPressure.PanicGcPolicy POLICY =
            new MantleHeapPressure.PanicGcPolicy(10_000L, 60_000L, 60_000L, 240_000L);

    @Test
    public void normalPressureNeverRequestsEitherGcPath() {
        ReclaimerFixture fixture = new ReclaimerFixture(0);

        fixture.reclaimer.request(0.91D);
        fixture.clock.addAndGet(120_000L);
        fixture.reclaimer.request(0.91D);

        assertEquals(0, fixture.explicitCalls.get());
        assertEquals(0, fixture.diagnosticCalls.get());
    }

    @Test
    public void sustainedPanicInvokesDiagnosticOnlyAfterNormalReclaimGrace() {
        ReclaimerFixture fixture = new ReclaimerFixture(0);

        fixture.reclaimer.request(0.97D);
        fixture.clock.set(9_999L);
        fixture.reclaimer.request(0.97D);
        fixture.clock.set(10_000L);
        fixture.reclaimer.request(0.97D);
        fixture.clock.set(69_999L);
        fixture.reclaimer.request(0.99D);

        assertEquals(1, fixture.explicitCalls.get());
        assertEquals(1, fixture.diagnosticCalls.get());
        assertEquals(List.of(0.97D), fixture.diagnosticFractions);
    }

    @Test
    public void recoveredEpisodeResetsButDiagnosticCooldownStillApplies() {
        ReclaimerFixture fixture = new ReclaimerFixture(0);

        fixture.reclaimer.request(0.97D);
        fixture.clock.set(10_000L);
        fixture.reclaimer.request(0.97D);
        fixture.reclaimer.resetEpisode();
        fixture.clock.set(20_000L);
        fixture.reclaimer.request(0.97D);
        fixture.clock.set(30_000L);
        fixture.reclaimer.request(0.97D);
        fixture.clock.set(70_000L);
        fixture.reclaimer.request(0.97D);

        assertEquals(2, fixture.explicitCalls.get());
        assertEquals(2, fixture.diagnosticCalls.get());
    }

    @Test
    public void highWaterEpisodeDiagnosesAfterNormalReclaimLeavesHeapBelowHighWater() {
        ReclaimerFixture fixture = new ReclaimerFixture(0);

        fixture.reclaimer.request(0.93D);
        fixture.clock.set(9_999L);
        fixture.reclaimer.request(0.91D);
        fixture.clock.set(10_000L);
        fixture.reclaimer.request(0.91D);

        assertEquals(1, fixture.explicitCalls.get());
        assertEquals(1, fixture.diagnosticCalls.get());
        assertEquals(List.of(0.91D), fixture.diagnosticFractions);
    }

    @Test
    public void successfulDiagnosticRetriesAfterCooldownWhenPressureRemainsHigh() {
        ReclaimerFixture fixture = new ReclaimerFixture(0);

        fixture.reclaimer.request(0.97D);
        fixture.clock.set(10_000L);
        fixture.reclaimer.request(0.91D);
        fixture.clock.set(69_999L);
        fixture.reclaimer.request(0.91D);
        fixture.clock.set(70_000L);
        fixture.reclaimer.request(0.91D);

        assertEquals(1, fixture.explicitCalls.get());
        assertEquals(2, fixture.diagnosticCalls.get());
        assertEquals(List.of(0.91D, 0.91D), fixture.diagnosticFractions);
    }

    @Test
    public void failedDiagnosticRetriesAfterBackoffWithinSameEpisode() {
        ReclaimerFixture fixture = new ReclaimerFixture(1);

        fixture.reclaimer.request(0.97D);
        fixture.clock.set(10_000L);
        fixture.reclaimer.request(0.91D);
        fixture.clock.set(69_999L);
        fixture.reclaimer.request(0.91D);
        fixture.clock.set(70_000L);
        fixture.reclaimer.request(0.91D);

        assertEquals(1, fixture.explicitCalls.get());
        assertEquals(2, fixture.diagnosticCalls.get());
        assertEquals(1, fixture.failures.size());
    }

    @Test
    public void sustainedSubHighPressureReleasesAfterBoundedHysteresis() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicInteger releases = new AtomicInteger();
        MantleHeapPressure.HeapPressureGate gate = new MantleHeapPressure.HeapPressureGate(
                0.92D,
                0.82D,
                60_000L,
                clock::get,
                releases::incrementAndGet);

        assertEquals(true, gate.update(0.93D));
        assertEquals(true, gate.update(0.89D));
        clock.set(60_999L);
        assertEquals(true, gate.update(0.89D));
        clock.set(61_000L);
        assertEquals(false, gate.update(0.89D));
        assertEquals(1, releases.get());
        assertEquals(false, gate.update(0.89D));
    }

    @Test
    public void renewedHighPressureRestartsHysteresisAndCanReengageAfterRelease() {
        AtomicLong clock = new AtomicLong(5_000L);
        AtomicInteger releases = new AtomicInteger();
        MantleHeapPressure.HeapPressureGate gate = new MantleHeapPressure.HeapPressureGate(
                0.92D,
                0.82D,
                60_000L,
                clock::get,
                releases::incrementAndGet);

        assertEquals(true, gate.update(0.93D));
        assertEquals(true, gate.update(0.88D));
        clock.set(64_999L);
        assertEquals(true, gate.update(0.93D));
        assertEquals(true, gate.update(0.88D));
        clock.set(124_998L);
        assertEquals(true, gate.update(0.88D));
        clock.set(124_999L);
        assertEquals(false, gate.update(0.88D));
        assertEquals(1, releases.get());
        assertEquals(true, gate.update(0.92D));
    }

    @Test
    public void lowWaterReleasesImmediately() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger releases = new AtomicInteger();
        MantleHeapPressure.HeapPressureGate gate = new MantleHeapPressure.HeapPressureGate(
                0.92D,
                0.82D,
                60_000L,
                clock::get,
                releases::incrementAndGet);

        assertEquals(true, gate.update(0.95D));
        assertEquals(false, gate.update(0.82D));
        assertEquals(1, releases.get());
    }

    @Test
    public void diagnosticInvocationUsesCurrentJvmCommandMBean() throws Exception {
        MBeanServer server = mock(MBeanServer.class);
        when(server.isRegistered(any(ObjectName.class))).thenReturn(true);

        MantleHeapPressure.invokeHotSpotDiagnosticGc(server);

        ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<String[]> signature = ArgumentCaptor.forClass(String[].class);
        verify(server).invoke(
                eq(name),
                eq("gcRun"),
                parameters.capture(),
                signature.capture());
        assertArrayEquals(new Object[0], parameters.getValue());
        assertArrayEquals(new String[0], signature.getValue());
    }

    @Test
    public void unsupportedJvmFailsBeforeInvokingDiagnosticCommand() throws Exception {
        MBeanServer server = mock(MBeanServer.class);
        when(server.isRegistered(any(ObjectName.class))).thenReturn(false);

        assertThrows(UnsupportedOperationException.class, () -> MantleHeapPressure.invokeHotSpotDiagnosticGc(server));
        verify(server, never()).invoke(any(ObjectName.class), any(), any(), any());
    }

    private static final class ReclaimerFixture {
        private final AtomicLong clock = new AtomicLong();
        private final AtomicInteger explicitCalls = new AtomicInteger();
        private final AtomicInteger diagnosticCalls = new AtomicInteger();
        private final List<Double> diagnosticFractions = new ArrayList<>();
        private final List<Throwable> failures = new ArrayList<>();
        private final MantleHeapPressure.PanicGcReclaimer reclaimer;

        private ReclaimerFixture(int failuresBeforeSuccess) {
            MantleHeapPressure.PanicGcActions actions = new MantleHeapPressure.PanicGcActions(
                    clock::get,
                    explicitCalls::incrementAndGet,
                    () -> {
                        int call = diagnosticCalls.incrementAndGet();
                        if (call <= failuresBeforeSuccess) {
                            throw new IllegalStateException("unsupported");
                        }
                    },
                    diagnosticFractions::add,
                    (String context, Throwable failure) -> failures.add(failure));
            this.reclaimer = new MantleHeapPressure.PanicGcReclaimer(POLICY, actions);
        }
    }
}
