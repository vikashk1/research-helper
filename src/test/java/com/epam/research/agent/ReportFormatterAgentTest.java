package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportFormatterAgentTest {

    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messageService;
    @Mock private Message message;
    @Mock private ContentBlock contentBlock;
    @Mock private TextBlock textBlock;

    @InjectMocks
    private ReportFormatterAgent reportFormatterAgent;

    private void stubApiCall(String responseText) {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
    }

    @Test
    void should_returnFormattedReport_when_validInputsProvided() {
        stubApiCall("# Climate Change Report\n\n## Key Findings\n...");
        SummaryResult summaryResult = new SummaryResult("Summarized content...", List.of());

        String result = reportFormatterAgent.format(
                "climate change", "focus on 2020-2025", summaryResult);

        assertThat(result).contains("# Climate Change Report").contains("## Key Findings");
    }

    @Test
    void should_appendSourcesSection_when_summaryResultHasSources() {
        stubApiCall("# Report\n\nSome content [1].");
        SummaryResult summaryResult = new SummaryResult(
                "summary", List.of("https://source1.com", "https://source2.com"));

        String result = reportFormatterAgent.format("topic", "context", summaryResult);

        assertThat(result)
                .contains("## Sources")
                .contains("[1] [https://source1.com](https://source1.com)")
                .contains("[2] [https://source2.com](https://source2.com)");
    }

    @Test
    void should_notAppendSourcesSection_when_summaryResultHasNoSources() {
        stubApiCall("# Report\n\nContent without sources.");
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        String result = reportFormatterAgent.format("topic", "context", summaryResult);

        assertThat(result).doesNotContain("## Sources");
    }

    @Test
    void should_includeTopicContextAndSummary_when_buildingApiRequest() {
        stubApiCall("# Report");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SummaryResult summaryResult = new SummaryResult("summarized content here", List.of());

        reportFormatterAgent.format("quantum computing", "target engineers", summaryResult);

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
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        reportFormatterAgent.format("topic", "context", summaryResult);

        verify(messageService).create(captor.capture());
        assertThat(captor.getValue().tools())
                .satisfies(tools -> assertThat(tools.orElse(List.of())).isEmpty());
    }

    @Test
    void should_useCorrectModelAndMaxTokens_when_buildingApiRequest() {
        stubApiCall("# Report");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        reportFormatterAgent.format("topic", "context", summaryResult);

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.model()).isEqualTo(Model.CLAUDE_SONNET_4_6);
        assertThat(params.maxTokens()).isGreaterThanOrEqualTo(2048L);
    }

    @Test
    void should_returnEmpty_when_contentListIsEmpty() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of());
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        String result = reportFormatterAgent.format("topic", "context", summaryResult);

        assertThat(result).isEmpty();
    }

    @Test
    void should_skipNonTextBlocks_when_contentBlockHasNoText() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.empty());
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        String result = reportFormatterAgent.format("topic", "context", summaryResult);

        assertThat(result).isEmpty();
    }

    @Test
    void should_concatenateAcrossBlocks_when_responseSpansMultipleContentBlocks() {
        ContentBlock secondBlock = mock(ContentBlock.class);
        TextBlock secondTextBlock = mock(TextBlock.class);

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock, secondBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("# Section One");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("## Section Two");
        SummaryResult summaryResult = new SummaryResult("summary", List.of());

        String result = reportFormatterAgent.format("topic", "context", summaryResult);

        assertThat(result).contains("# Section One").contains("## Section Two");
    }
}
