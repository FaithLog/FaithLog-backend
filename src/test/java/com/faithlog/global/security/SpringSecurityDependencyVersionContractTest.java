package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

class SpringSecurityDependencyVersionContractTest {

	private static final String RUNTIME_MANIFEST_PROPERTY =
		"faithlog.spring-security.runtime-classpath-manifest";
	private static final String TEST_RUNTIME_MANIFEST_PROPERTY =
		"faithlog.spring-security.test-runtime-classpath-manifest";
	private static final Version MINIMUM_SAFE_VERSION = Version.parse("6.5.11");
	private static final String SAFE_RUNTIME_MANIFEST = String.join(",",
		"spring-security-config=6.5.11",
		"spring-security-core=6.5.11",
		"spring-security-crypto=6.5.11",
		"spring-security-web=6.5.11"
	);
	private static final String SAFE_TEST_RUNTIME_MANIFEST = SAFE_RUNTIME_MANIFEST
		+ ",spring-security-test=6.5.11";

	@Test
	void all_resolved_spring_security_modules_are_at_least_6_5_11() throws IOException {
		Map<String, Version> resolvedModules = springSecurityModulesOnTestRuntimeClasspath();

		assertThat(resolvedModules)
			.containsKeys(
				"spring-security-config",
				"spring-security-core",
				"spring-security-crypto",
				"spring-security-web"
			);
		assertThat(resolvedModules)
			.allSatisfy((module, version) -> assertThat(version)
				.as("%s must be at least %s but resolved %s", module, MINIMUM_SAFE_VERSION, version)
				.isGreaterThanOrEqualTo(MINIMUM_SAFE_VERSION));
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
	void prerelease_or_qualified_version_is_not_an_approved_oss_release() {
		assertThatThrownBy(() -> Version.parse("6.5.11-RC1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("6.5.11-RC1");
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

	private Map<String, Version> springSecurityModulesOnTestRuntimeClasspath() throws IOException {
		Map<String, Version> modules = new LinkedHashMap<>();
		for (URL manifestUrl : Collections.list(
			Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF")
		)) {
			try (var input = manifestUrl.openStream()) {
				Attributes attributes = new Manifest(input).getMainAttributes();
				String title = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
				String version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
				if (title != null && title.startsWith("spring-security-") && version != null) {
					modules.put(title, Version.parse(version));
				}
			}
		}
		return modules;
	}

	private record Version(int major, int minor, int patch) implements Comparable<Version> {

		private static Version parse(String value) {
			String release = value.split("[-+]", 2)[0];
			String[] components = release.split("\\.");
			if (components.length < 3) {
				throw new IllegalArgumentException("Unsupported version: " + value);
			}
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
