package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.notification.application.port.FcmSendPort;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration
@Profile("!local & !test")
public class FirebaseAdminFcmConfig {

	private static final String APP_NAME = "faithlog-notification";

	@Bean
	FirebaseApp firebaseApp(
		@Value("${faithlog.firebase.config-json:}") String configJson,
		@Value("${faithlog.firebase.config-path:}") String configPath
	) throws IOException {
		FirebaseApp existingApp = findExistingApp();
		if (existingApp != null) {
			return existingApp;
		}
		try (InputStream credentialsStream = credentialsStream(configJson, configPath)) {
			FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.fromStream(credentialsStream))
				.build();
			return FirebaseApp.initializeApp(options, APP_NAME);
		}
	}

	@Bean
	FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
		return FirebaseMessaging.getInstance(firebaseApp);
	}

	@Bean
	FirebaseMessagingClient firebaseMessagingClient(FirebaseMessaging firebaseMessaging) {
		return new FirebaseAdminMessagingClient(firebaseMessaging);
	}

	@Bean
	FcmSendPort fcmSendPort(FirebaseMessagingClient firebaseMessagingClient) {
		return new FirebaseFcmSendAdapter(firebaseMessagingClient);
	}

	private FirebaseApp findExistingApp() {
		return FirebaseApp.getApps()
			.stream()
			.filter(app -> APP_NAME.equals(app.getName()))
			.findFirst()
			.orElse(null);
	}

	private InputStream credentialsStream(String configJson, String configPath) throws IOException {
		if (StringUtils.hasText(configJson)) {
			return new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8));
		}
		if (StringUtils.hasText(configPath)) {
			return Files.newInputStream(Path.of(configPath));
		}
		throw new IllegalStateException(
			"Firebase Admin credentials are required outside local/test profiles. "
				+ "Set FIREBASE_CONFIG_JSON or FIREBASE_CONFIG_PATH."
		);
	}
}
