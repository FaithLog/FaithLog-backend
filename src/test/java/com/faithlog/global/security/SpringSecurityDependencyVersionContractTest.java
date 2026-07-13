package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpringSecurityDependencyVersionContractTest {

	private static final String RUNTIME_MANIFEST_PROPERTY =
		"faithlog.spring-security.runtime-classpath-manifest";
	private static final String TEST_RUNTIME_MANIFEST_PROPERTY =
		"faithlog.spring-security.test-runtime-classpath-manifest";
	private static final Version MINIMUM_SAFE_VERSION = Version.parse("6.5.11");
	private static final Set<String> REQUIRED_RUNTIME_MODULES = Set.of(
		"spring-security-config",
		"spring-security-core",
		"spring-security-crypto",
		"spring-security-web"
	);
	private static final Set<String> REQUIRED_TEST_RUNTIME_MODULES = Set.of(
		"spring-security-config",
		"spring-security-core",
		"spring-security-crypto",
		"spring-security-test",
		"spring-security-web"
	);
	private static final String SAFE_RUNTIME_MANIFEST = String.join(",",
		"spring-security-config=6.5.11",
		"spring-security-core=6.5.11",
		"spring-security-crypto=6.5.11",
		"spring-security-web=6.5.11"
	);
	private static final String SAFE_TEST_RUNTIME_MANIFEST = SAFE_RUNTIME_MANIFEST
		+ ",spring-security-test=6.5.11";

	@Test
	void all_resolved_spring_security_modules_are_at_least_6_5_11() {
		assertSafeClasspath(
			"runtimeClasspath",
			System.getProperty(RUNTIME_MANIFEST_PROPERTY),
			REQUIRED_RUNTIME_MODULES
		);
		assertSafeClasspath(
			"testRuntimeClasspath",
			System.getProperty(TEST_RUNTIME_MANIFEST_PROPERTY),
			REQUIRED_TEST_RUNTIME_MODULES
		);
	}

	@Test
	void vulnerable_runtime_classpath_cannot_be_masked_by_safe_test_runtime_classpath() {
		String vulnerableRuntimeManifest = SAFE_RUNTIME_MANIFEST.replace("6.5.11", "6.5.10");

		withDependencyManifests(vulnerableRuntimeManifest, SAFE_TEST_RUNTIME_MANIFEST, () ->
			assertThatThrownBy(this::all_resolved_spring_security_modules_are_at_least_6_5_11)
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("runtimeClasspath")
				.hasMessageContaining("6.5.10")
		);
	}

	@Test
	void vulnerable_test_runtime_classpath_is_validated_independently() {
		String vulnerableTestRuntimeManifest = SAFE_TEST_RUNTIME_MANIFEST.replace("6.5.11", "6.5.10");

		withDependencyManifests(SAFE_RUNTIME_MANIFEST, vulnerableTestRuntimeManifest, () ->
			assertThatThrownBy(this::all_resolved_spring_security_modules_are_at_least_6_5_11)
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("testRuntimeClasspath")
				.hasMessageContaining("6.5.10")
		);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"6.5.11-RC1",
		"6.5.11-M1",
		"6.5.11-SNAPSHOT",
		"6.5.11.Final",
		"6.5.11+build.1",
		"6.5",
		"6.5.11.0"
	})
	void prerelease_qualified_or_non_three_part_version_is_not_an_approved_oss_release(String version) {
		assertThatThrownBy(() -> Version.parse(version))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(version);
	}

	private void withDependencyManifests(String runtimeManifest, String testRuntimeManifest, Runnable assertion) {
		String previousRuntimeManifest = System.getProperty(RUNTIME_MANIFEST_PROPERTY);
		String previousTestRuntimeManifest = System.getProperty(TEST_RUNTIME_MANIFEST_PROPERTY);
		try {
			System.setProperty(RUNTIME_MANIFEST_PROPERTY, runtimeManifest);
			System.setProperty(TEST_RUNTIME_MANIFEST_PROPERTY, testRuntimeManifest);
			assertion.run();
		} finally {
			restoreSystemProperty(RUNTIME_MANIFEST_PROPERTY, previousRuntimeManifest);
			restoreSystemProperty(TEST_RUNTIME_MANIFEST_PROPERTY, previousTestRuntimeManifest);
		}
	}

	private void restoreSystemProperty(String name, String previousValue) {
		if (previousValue == null) {
			System.clearProperty(name);
			return;
		}
		System.setProperty(name, previousValue);
	}

	private void assertSafeClasspath(String classpath, String manifest, Set<String> requiredModules) {
		assertThat(manifest)
			.as("%s Spring Security resolved manifest must be supplied by Gradle", classpath)
			.isNotBlank();

		Map<String, Version> resolvedModules = parseManifest(classpath, manifest);
		assertThat(resolvedModules)
			.as("%s must contain its required Spring Security modules", classpath)
			.containsKeys(requiredModules.toArray(String[]::new));
		assertThat(resolvedModules)
			.allSatisfy((module, version) -> assertThat(version)
				.as(
					"%s %s must be at least %s but resolved %s",
					classpath,
					module,
					MINIMUM_SAFE_VERSION,
					version
				)
				.isGreaterThanOrEqualTo(MINIMUM_SAFE_VERSION));
	}

	private Map<String, Version> parseManifest(String classpath, String manifest) {
		Map<String, Version> modules = new LinkedHashMap<>();
		for (String coordinate : manifest.split(",")) {
			String[] components = coordinate.split("=", -1);
			if (components.length != 2 || components[0].isBlank() || components[1].isBlank()) {
				throw new IllegalArgumentException("Malformed " + classpath + " manifest entry: " + coordinate);
			}
			String module = components[0];
			Version previous = modules.putIfAbsent(module, Version.parse(components[1]));
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate " + classpath + " module: " + module);
			}
		}
		return modules;
	}

	private record Version(int major, int minor, int patch) implements Comparable<Version> {

		private static Version parse(String value) {
			if (!value.matches("\\d+\\.\\d+\\.\\d+")) {
				throw new IllegalArgumentException("Unsupported version: " + value);
			}
			String[] components = value.split("\\.");
			return new Version(
				Integer.parseInt(components[0]),
				Integer.parseInt(components[1]),
				Integer.parseInt(components[2])
			);
		}

		@Override
		public int compareTo(Version other) {
			int majorComparison = Integer.compare(major, other.major);
			if (majorComparison != 0) {
				return majorComparison;
			}
			int minorComparison = Integer.compare(minor, other.minor);
			if (minorComparison != 0) {
				return minorComparison;
			}
			return Integer.compare(patch, other.patch);
		}

		@Override
		public String toString() {
			return major + "." + minor + "." + patch;
		}
	}
}
