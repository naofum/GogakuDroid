package com.github.naofum.gogakudroid;

/**
 * Marker interface for network-dependent integration tests.
 * Tests annotated with {@code @Category(NetworkTest.class)} are excluded from the
 * default {@code testDebugUnitTest} run and executed only via the
 * {@code integrationTest} Gradle task.
 */
public interface NetworkTest {
}
