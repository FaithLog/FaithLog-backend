import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
	java
	jacoco
	id("org.springframework.boot") version "3.5.15"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "com.faithlog"
version = "0.1.0"
description = "FaithLog Backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val snippetsDir = layout.buildDirectory.dir("generated-snippets")
val runtimeSpringSecurityManifest = providers.provider {
	configurations.getByName("runtimeClasspath")
		.incoming
		.artifacts
		.artifacts
		.asSequence()
		.mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
		.filter { it.group == "org.springframework.security" }
		.map { "${it.module}=${it.version}" }
		.sorted()
		.joinToString(",")
}
val testRuntimeSpringSecurityManifest = providers.provider {
	configurations.getByName("testRuntimeClasspath")
		.incoming
		.artifacts
		.artifacts
		.asSequence()
		.mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
		.filter { it.group == "org.springframework.security" }
		.map { "${it.module}=${it.version}" }
		.sorted()
		.joinToString(",")
}

configurations.configureEach {
	resolutionStrategy.eachDependency {
		if (requested.group == "io.netty") {
			useVersion("4.1.135.Final")
			because("Override Spring Boot managed Netty version to address reported Netty CVEs.")
		}
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.flywaydb:flyway-core")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
	implementation("com.google.firebase:firebase-admin:9.9.0")
	implementation("org.apache.commons:commons-lang3:3.20.0")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	outputs.dir(snippetsDir)
	inputs.property("springSecurityRuntimeClasspathManifest", runtimeSpringSecurityManifest)
	inputs.property("springSecurityTestRuntimeClasspathManifest", testRuntimeSpringSecurityManifest)
	doFirst {
		systemProperty(
			"faithlog.spring-security.runtime-classpath-manifest",
			runtimeSpringSecurityManifest.get()
		)
		systemProperty(
			"faithlog.spring-security.test-runtime-classpath-manifest",
			testRuntimeSpringSecurityManifest.get()
		)
	}
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		html.required.set(true)
		xml.required.set(true)
		csv.required.set(false)
	}
}

tasks.named<org.asciidoctor.gradle.jvm.AsciidoctorTask>("asciidoctor") {
	dependsOn(tasks.test)
	inputs.dir(snippetsDir)
	baseDirFollowsSourceDir()
	attributes(mapOf("snippets" to snippetsDir.get().asFile))
}
