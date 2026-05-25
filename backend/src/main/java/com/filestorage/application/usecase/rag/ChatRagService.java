package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import com.filestorage.adapter.out.persistence.entity.RagChatMessageSource;
import com.filestorage.adapter.out.persistence.entity.RagChatSession;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageSourceJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatSessionJpaRepository;
import com.filestorage.domain.model.enums.RagChatMessageRole;
import com.filestorage.domain.model.rag.ChatCompletion;
import com.filestorage.domain.model.rag.ChatPrompt;
import com.filestorage.domain.model.rag.RagChatMessageView;
import com.filestorage.domain.model.rag.RagChatView;
import com.filestorage.domain.model.rag.RagRetrievalRequest;
import com.filestorage.domain.model.rag.RagRetrievedChunk;
import com.filestorage.domain.model.rag.RagSourceView;
import com.filestorage.domain.port.in.rag.ChatRagUseCase;
import com.filestorage.domain.port.out.ChatModelPort;
import com.filestorage.infrastructure.config.RagProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatRagService implements ChatRagUseCase {

    private static final String NO_CONTEXT_ANSWER =
            "Tôi không tìm thấy thông tin phù hợp trong các tài liệu bạn có quyền truy cập.";
    private static final int FOLLOW_UP_MAX_CHARS = 80;
    private static final Pattern INLINE_INTERNAL_CITATION_PATTERN = Pattern.compile(
            "\\s*\\(\\s*(?:[Đđ]oạn|[Dd]oan|[Cc]hunk|[Ss]ource)\\s+\\d+\\s*:[^)]*\\)"
    );
    private static final List<String> FOLLOW_UP_TRIGGERS = List.of(
            "chi tiet",
            "chi tiet hon",
            "ngan gon",
            "ngan gon hon",
            "tom tat",
            "tom tat lai",
            "giai thich them",
            "giai thich ky hon",
            "giai thich de hieu hon",
            "noi ro hon",
            "lam ro",
            "lam ro hon",
            "vi du",
            "cho vi du",
            "tiep tuc",
            "noi tiep"
    );

    private final RagChatSessionService sessionService;
    private final RagChatMessageJpaRepository messageRepository;
    private final RagChatMessageSourceJpaRepository sourceRepository;
    private final RagChatSessionJpaRepository sessionRepository;
    private final RagRetrievalService retrievalService;
    private final RagPromptBuilder promptBuilder;
    private final ChatModelPort chatModelPort;
    private final RagProperties ragProperties;
    private final RagChatMapper mapper;

    @Override
    public RagChatView execute(ChatRagCommand command) {
        validate(command);
        String question = command.message().trim();
        RagChatSession session = sessionService.loadActiveSession(
                command.sessionId(),
                command.actorTenantId(),
                command.actorUserId()
        );

        List<RagChatMessage> history = loadHistory(session.getId());
        RagChatMessage userMessage = messageRepository.save(RagChatMessage.builder()
                .sessionId(session.getId())
                .role(RagChatMessageRole.USER)
                .content(question)
                .build());

        String retrievalQuestion = buildRetrievalQuestion(question, history);
        List<RagRetrievedChunk> chunks = retrievalService.retrieve(new RagRetrievalRequest(
                command.actorTenantId(),
                command.actorUserId(),
                command.actorRole(),
                retrievalQuestion,
                command.topK()
        ));

        int topK = command.topK() == null ? ragProperties.getRetrieval().getTopK() : command.topK();
        AssistantResult assistantResult = chunks.isEmpty()
                ? noContextResult()
                : completeWithModel(question, chunks, history);

        RagChatMessage assistantMessage = messageRepository.save(RagChatMessage.builder()
                .sessionId(session.getId())
                .role(RagChatMessageRole.ASSISTANT)
                .content(assistantResult.content())
                .model(assistantResult.model())
                .retrievalTopK(topK)
                .inputTokens(assistantResult.inputTokens())
                .outputTokens(assistantResult.outputTokens())
                .build());

        List<RagChatMessageSource> savedSources = saveSources(assistantMessage, chunks);
        updateSessionAfterChat(session, question);

        List<RagSourceView> sourceViews = savedSources.stream().map(this::toSourceView).toList();
        RagChatMessageView userView = mapper.toMessageView(userMessage, List.of());
        RagChatMessageView assistantView = mapper.toMessageView(assistantMessage, sourceViews);
        return new RagChatView(mapper.toSessionView(session), userView, assistantView);
    }

    private AssistantResult completeWithModel(String question, List<RagRetrievedChunk> chunks, List<RagChatMessage> history) {
        ChatPrompt prompt = promptBuilder.build(question, chunks, history);
        ChatCompletion completion = chatModelPort.complete(prompt);
        return new AssistantResult(
                normalizeAssistantContent(completion.content()),
                completion.model(),
                completion.inputTokens(),
                completion.outputTokens()
        );
    }

    private AssistantResult noContextResult() {
        return new AssistantResult(NO_CONTEXT_ANSWER, ragProperties.getChat().getModel(), null, null);
    }

    private List<RagChatMessage> loadHistory(java.util.UUID sessionId) {
        int limit = ragProperties.getChat().getMaxHistoryMessages();
        if (limit <= 0) {
            return List.of();
        }
        List<RagChatMessage> recent = new ArrayList<>(messageRepository.findRecentBySessionId(
                sessionId,
                PageRequest.of(0, limit)
        ));
        Collections.reverse(recent);
        return recent;
    }

    private String buildRetrievalQuestion(String question, List<RagChatMessage> history) {
        if (!isFollowUpQuestion(question)) {
            return question;
        }
        return lastUserQuestion(history)
                .map(previousQuestion -> previousQuestion + "\n" + question)
                .orElse(question);
    }

    private static boolean isFollowUpQuestion(String question) {
        String normalized = normalizeForMatching(question);
        if (normalized.length() > FOLLOW_UP_MAX_CHARS) {
            return false;
        }
        return FOLLOW_UP_TRIGGERS.stream().anyMatch(trigger ->
                normalized.equals(trigger) || normalized.startsWith(trigger + " ")
        );
    }

    private static Optional<String> lastUserQuestion(List<RagChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            RagChatMessage message = history.get(i);
            if (message.getRole() == RagChatMessageRole.USER
                    && message.getContent() != null
                    && !message.getContent().isBlank()) {
                return Optional.of(message.getContent().trim());
            }
        }
        return Optional.empty();
    }

    private static String normalizeForMatching(String value) {
        String withoutSpecialD = value.replace('\u0111', 'd').replace('\u0110', 'D');
        return Normalizer.normalize(withoutSpecialD, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeAssistantContent(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = INLINE_INTERNAL_CITATION_PATTERN.matcher(content).replaceAll("");
        return removeTrailingSourceBlock(cleaned).strip();
    }

    private static String removeTrailingSourceBlock(String content) {
        String[] lines = content.split("\\R", -1);
        int sourceHeadingIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isSourceHeading(lines[i])) {
                sourceHeadingIndex = i;
                break;
            }
        }
        if (sourceHeadingIndex < 0) {
            return content;
        }

        int endExclusive = sourceHeadingIndex;
        while (endExclusive > 0 && isHorizontalRule(lines[endExclusive - 1])) {
            endExclusive--;
        }
        return String.join("\n", Arrays.copyOf(lines, endExclusive));
    }

    private static boolean isSourceHeading(String line) {
        String normalized = normalizeForMatching(line.replaceAll("[*#:_\\-]+", " "));
        return normalized.equals("nguon tham khao")
                || normalized.equals("source")
                || normalized.equals("sources")
                || normalized.equals("reference")
                || normalized.equals("references")
                || normalized.equals("tai lieu tham khao");
    }

    private static boolean isHorizontalRule(String line) {
        String trimmed = line.trim();
        return trimmed.matches("-{3,}");
    }

    private List<RagChatMessageSource> saveSources(RagChatMessage assistantMessage, List<RagRetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<RagChatMessageSource> sources = chunks.stream()
                .map(chunk -> RagChatMessageSource.builder()
                        .messageId(assistantMessage.getId())
                        .fileId(chunk.fileId())
                        .originalName(chunk.originalName())
                        .chunkIndex(chunk.chunkIndex())
                        .snippet(snippet(chunk.content()))
                        .score(chunk.score())
                        .sourceKey(chunk.sourceKey())
                        .build())
                .toList();
        return sourceRepository.saveAll(sources);
    }

    private void updateSessionAfterChat(RagChatSession session, String question) {
        session.setLastMessageAt(Instant.now());
        if (session.getTitle() == null || session.getTitle().isBlank() || "New chat".equals(session.getTitle())) {
            session.setTitle(titleFromQuestion(question));
        }
        sessionRepository.save(session);
    }

    private RagSourceView toSourceView(RagChatMessageSource source) {
        return new RagSourceView(
                source.getFileId(),
                source.getOriginalName(),
                source.getChunkIndex(),
                source.getSnippet(),
                source.getScore()
        );
    }

    private static String snippet(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private static String titleFromQuestion(String question) {
        return question.length() <= 80 ? question : question.substring(0, 80);
    }

    private void validate(ChatRagCommand command) {
        if (command == null
                || command.actorUserId() == null
                || command.actorTenantId() == null
                || command.actorRole() == null
                || command.sessionId() == null
                || command.message() == null
                || command.message().isBlank()
                || command.message().trim().length() > ragProperties.getChat().getMaxMessageChars()
                || isInvalidTopK(command.topK())) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private boolean isInvalidTopK(Integer topK) {
        return topK != null && (topK < 1 || topK > ragProperties.getRetrieval().getMaxTopK());
    }

    private record AssistantResult(
            String content,
            String model,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }
}
