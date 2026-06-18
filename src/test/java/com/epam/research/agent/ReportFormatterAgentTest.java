package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.epam.research.job.JobService;
import com.epam.research.agent.AgentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import com.anthropic.models.messages.StopReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportFormatterAgentTest {

    private static final Long JOB_ID = 1L;

    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messageService;
    @Mock private Message message;
    @Mock private Usage usage;
    @Mock private ContentBlock contentBlock;
    @Mock private TextBlock textBlock;
    @Mock private JobService jobService;

    @InjectMocks
    private ReportFormatterAgent reportFormatterAgent;

    @BeforeEach
    void stubUsage() {
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(10L);
        when(usage.outputTokens()).thenReturn(20L);
    }

    private void stubApiCall(String responseText) {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(80L);
        when(usage.outputTokens()).thenReturn(40L);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
        when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
    }

    @Test
    void should_returnFormattedReport_when_validInputsProvided() {
        stubApiCall("# Climate Change Report\n\n## Key Findings\n...");

        AgentResult result = reportFormatterAgent.format(
                JOB_ID, "climate change", "focus on 2020-2025", "Summarized content...");

        assertThat(result.content()).isEqualTo("# Climate Change Report\n\n## Key Findings\n...");
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("FORMAT"), eq("start"), contains("climate change"));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("FORMAT"), eq("activity"), contains("Assembling report sections..."));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("FORMAT"), eq("activity"), contains("Report formatting complete"));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("FORMAT"), eq("end"), contains("Report formatting complete"));
    }

    @Test
    void should_includeTopicContextAndSummary_when_buildingApiRequest() {
        stubApiCall("# Report");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        reportFormatterAgent.format(JOB_ID, "quantum computing", "target engineers", "summarized content here");

        verify(messageService).create(captor.capture());
        String userMessage = captor.getValue().messages().get(0).content().asString();
        assertThat(userMessage)
                .contains("quantum computing")
                .contains("target engineers")
                .contains("summarized content here");
    }

    @Test
    void should_notIncludeAnyTool_when_buildingApiRequest() {
        stubApiCall("# Report");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        verify(messageService).create(captor.capture());
        assertThat(captor.getValue().tools())
                .satisfies(tools -> assertThat(tools.orElse(List.of())).isEmpty());
    }

    @Test
    void should_useCorrectModelAndMaxTokens_when_buildingApiRequest() {
        stubApiCall("# Report");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.model()).isEqualTo(Model.CLAUDE_HAIKU_4_5);
        assertThat(params.maxTokens()).isGreaterThanOrEqualTo(8192L);
    }

    @Test
    void should_callAddTokenUsage_when_formatCompletes() {
        stubApiCall("# Report");

        AgentResult result = reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        assertThat(result.inputTokens()).isEqualTo(80L);
        assertThat(result.outputTokens()).isEqualTo(40L);
    }

    @Test
    void should_returnEmpty_when_contentListIsEmpty() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(0L);
        when(usage.outputTokens()).thenReturn(0L);
        when(message.content()).thenReturn(List.of());

        AgentResult result = reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        assertThat(result.content()).isEmpty();
    }

    @Test
    void should_skipNonTextBlocks_when_contentBlockHasNoText() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(0L);
        when(usage.outputTokens()).thenReturn(0L);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.empty());

        AgentResult result = reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        assertThat(result.content()).isEmpty();
    }

    @Test
    void should_concatenateAcrossBlocks_when_responseSpansMultipleContentBlocks() {
        ContentBlock secondBlock = mock(ContentBlock.class);
        TextBlock secondTextBlock = mock(TextBlock.class);

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(80L);
        when(usage.outputTokens()).thenReturn(40L);
        when(message.content()).thenReturn(List.of(contentBlock, secondBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("# Section One");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("## Section Two");

        AgentResult result = reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        assertThat(result.content()).contains("# Section One").contains("## Section Two");
    }

    @Test
    void should_logWarningAndAppendEvent_when_stopReasonIsMaxTokens() {
        stubApiCall("# Truncated Report");
        when(message.stopReason()).thenReturn(Optional.of(StopReason.MAX_TOKENS));

        reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        verify(jobService).appendStageEvent(eq(JOB_ID), eq("FORMAT"), eq("warning"),
                contains("truncated due to token limit"));
    }

    @Test
    void should_captureStopReason_when_responseReceived() {
        stubApiCall("# Report");
        when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        reportFormatterAgent.format(JOB_ID, "topic", "context", "summary");

        verify(messageService).create(captor.capture());
        assertThat(captor.getValue().maxTokens()).isEqualTo(8192L);
    }
}
