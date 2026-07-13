package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

class SpringSecurityDependencyVersionContractTest {

	private static final Version MINIMUM_SAFE_VERSION = Version.parse("6.5.11");

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
