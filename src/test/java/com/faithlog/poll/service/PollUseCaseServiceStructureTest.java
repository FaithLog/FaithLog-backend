package com.faithlog.poll.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PollUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/poll/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void pollUseCasesAreSeparatedIntoCohesiveTransactionalServices() {
		Map<String, List<TransactionalUseCase>> responsibilities = Map.of(
			"PollCreationCommandService.java", List.of(write("createPoll")),
			"PollStatusCommandService.java", List.of(write("closePoll")),
			"PollResponseCommandService.java", List.of(write("respondToPoll")),
			"PollQueryService.java", List.of(
				write("listPolls"),
				write("getPoll"),
				write("getPollDetail")
			),
			"PollResultQueryService.java", List.of(
				write("getPollResults"),
				readOnly("getMissingMembers")
			),
			"PollCommentCommandService.java", List.of(
				write("createComment"),
				write("updateComment"),
				write("deleteComment")
			),
			"PollCommentQueryService.java", List.of(write("listComments")),
			"PollUserOptionCommandService.java", List.of(write("addUserOption"))
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(useCase -> assertTrue(
				useCase.pattern().matcher(content).find(),
				entry.getKey() + "의 " + useCase.method() + "가 기존 트랜잭션 경계를 직접 가져야 합니다."
			));
		}));
	}

	@Test
	void eachDedicatedServiceOwnsOnlyItsApprovedPublicUseCases() {
		Map<String, List<String>> approvedMethods = Map.of(
			"PollCreationCommandService.java", List.of("createPoll"),
			"PollStatusCommandService.java", List.of("closePoll"),
			"PollResponseCommandService.java", List.of("respondToPoll"),
			"PollQueryService.java", List.of("listPolls", "getPoll", "getPollDetail"),
			"PollResultQueryService.java", List.of("getPollResults", "getMissingMembers"),
			"PollCommentCommandService.java", List.of("createComment", "updateComment", "deleteComment"),
			"PollCommentQueryService.java", List.of("listComments"),
			"PollUserOptionCommandService.java", List.of("addUserOption")
		);

		List<String> allMethods = approvedMethods.values().stream().flatMap(List::stream).distinct().toList();
		assertAll(approvedMethods.entrySet().stream().map(entry -> () -> {
			String content = read(SERVICE_ROOT.resolve(entry.getKey()));
			allMethods.stream()
				.filter(method -> !entry.getValue().contains(method))
				.forEach(method -> assertFalse(
					publicMethod(method).matcher(content).find(),
					entry.getKey() + "가 다른 유스케이스 " + method + "를 소유하면 안 됩니다."
				));
		}));
	}

	@Test
	void controllersUseDedicatedPollServices() {
		String pollController = read(MAIN_ROOT.resolve("poll/controller/PollController.java"));
		String adminPollController = read(MAIN_ROOT.resolve("poll/controller/AdminPollController.java"));

		assertAll(
			() -> assertTrue(pollController.contains("PollQueryService")),
			() -> assertTrue(pollController.contains("PollResponseCommandService")),
			() -> assertTrue(pollController.contains("PollResultQueryService")),
			() -> assertTrue(pollController.contains("PollCommentCommandService")),
			() -> assertTrue(pollController.contains("PollCommentQueryService")),
			() -> assertTrue(pollController.contains("PollUserOptionCommandService")),
			() -> assertFalse(pollController.contains("PollService")),
			() -> assertTrue(adminPollController.contains("PollCreationCommandService")),
			() -> assertTrue(adminPollController.contains("PollStatusCommandService")),
			() -> assertTrue(adminPollController.contains("PollResultQueryService")),
			() -> assertFalse(adminPollController.contains("PollService"))
		);
	}

	@Test
	void pollMutationsShareTheSettlementPessimisticWriteLookup() {
		String lookup = read(SERVICE_ROOT.resolve("PollLookupSupport.java"));
		List<String> mutationServices = List.of(
			"PollResponseCommandService.java",
			"PollUserOptionCommandService.java",
			"PollStatusCommandService.java",
			"MealPollService.java",
			"MealPollSettlementService.java"
		);

		assertAll(
			() -> assertTrue(lookup.contains("getPollInCampusForUpdate")),
			() -> assertTrue(lookup.contains("findByIdAndCampusIdForUpdate")),
			() -> assertAll(mutationServices.stream().map(service -> () -> assertTrue(
				read(SERVICE_ROOT.resolve(service)).contains("getPollInCampusForUpdate"),
				service + "가 settlement와 같은 poll PESSIMISTIC_WRITE 조회를 사용해야 합니다."
			)))
		);
	}

	@Test
	void compatibilityFacadeDoesNotOwnRepositoriesTransactionsOrBusinessRules() {
		String facade = read(SERVICE_ROOT.resolve("PollService.java"));

		assertAll(
			() -> assertFalse(facade.contains("Repository")),
			() -> assertFalse(facade.contains("@Transactional")),
			() -> assertFalse(facade.contains("BusinessException")),
			() -> assertFalse(facade.contains("ErrorCode")),
			() -> assertFalse(facade.contains("Poll.create(")),
			() -> assertFalse(facade.contains("PollResponse.create(")),
			() -> assertFalse(facade.contains("PollComment.create(")),
			() -> assertTrue(facade.contains("PollCreationCommandService")),
			() -> assertTrue(facade.contains("PollStatusCommandService")),
			() -> assertTrue(facade.contains("PollResponseCommandService")),
			() -> assertTrue(facade.contains("PollQueryService")),
			() -> assertTrue(facade.contains("PollResultQueryService")),
			() -> assertTrue(facade.contains("PollCommentCommandService")),
			() -> assertTrue(facade.contains("PollCommentQueryService")),
			() -> assertTrue(facade.contains("PollUserOptionCommandService"))
		);
	}

	@Test
	void dedicatedServicesDoNotFormDependencyCyclesOrUseCompatibilityFacade() {
		List<String> services = List.of(
			"PollCreationCommandService",
			"PollStatusCommandService",
			"PollResponseCommandService",
			"PollQueryService",
			"PollResultQueryService",
			"PollCommentCommandService",
			"PollCommentQueryService",
			"PollUserOptionCommandService"
		);

		assertAll(services.stream().map(service -> () -> {
			String content = read(SERVICE_ROOT.resolve(service + ".java"));
			assertFalse(content.contains("PollService"), service + "가 PollService에 의존하면 안 됩니다.");
			services.stream()
				.filter(other -> !other.equals(service))
				.forEach(other -> assertFalse(
					content.contains(other),
					service + "가 다른 전용 서비스 " + other + "에 의존하면 안 됩니다."
				));
		}));
	}

	private TransactionalUseCase write(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\s+public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\("
		));
	}

	private TransactionalUseCase readOnly(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\(readOnly\\s*=\\s*true\\)\\s+public\\s+[^\\n{]+\\s+"
				+ Pattern.quote(method)
				+ "\\s*\\("
		));
	}

	private Pattern publicMethod(String method) {
		return Pattern.compile("public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\(");
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record TransactionalUseCase(String method, Pattern pattern) {
	}
}
