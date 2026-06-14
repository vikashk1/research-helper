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
class SummarizerAgentTest {

    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messageService;
    @Mock private Message message;
    @Mock private ContentBlock contentBlock;
    @Mock private TextBlock textBlock;

    @InjectMocks
    private SummarizerAgent summarizerAgent;

    private void stubApiCall(String responseText) {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
    }

    @Test
    void should_returnSummary_when_validInputsProvided() {
        stubApiCall("Key findings: climate change is accelerating.");
        SearchResult searchResult = new SearchResult("Raw search result data...", List.of());

        SummaryResult result = summarizerAgent.summarize(
                "climate change", "focus on 2020-2025", searchResult);

        assertThat(result.summary()).isEqualTo("Key findings: climate change is accelerating.");
    }

    @Test
    void should_propagateSourceUrls_when_searchResultHasSources() {
        stubApiCall("Summary with citations [1].");
        List<String> sources = List.of("https://source1.com", "https://source2.com");
        SearchResult searchResult = new SearchResult("raw results", sources);

        SummaryResult result = summarizerAgent.summarize("topic", "context", searchResult);

        assertThat(result.sourceUrls()).containsExactlyElementsOf(sources);
    }

    @Test
    void should_includeTopicContextAndRawResults_when_buildingApiRequest() {
        stubApiCall("summary");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SearchResult searchResult = new SearchResult("raw results here", List.of());

        summarizerAgent.summarize("quantum computing", "target engineers", searchResult);

        verify(messageService).create(captor.capture());
        String userMessage = captor.getValue().messages().get(0).content().asString();
        assertThat(userMessage)
                .contains("quantum computing")
                .contains("target engineers")
                .contains("raw results here");
    }

    @Test
    void should_includeNumberedSourcesInPrompt_when_searchResultHasSources() {
        stubApiCall("summary");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SearchResult searchResult = new SearchResult("raw results", List.of("https://example.com/a"));

        summarizerAgent.summarize("topic", "context", searchResult);

        verify(messageService).create(captor.capture());
        String userMessage = captor.getValue().messages().get(0).content().asString();
        assertThat(userMessage)
                .contains("[1]")
                .contains("https://example.com/a");
    }

    @Test
    void should_notIncludeAnyTool_when_buildingApiRequest() {
        stubApiCall("summary");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SearchResult searchResult = new SearchResult("raw results", List.of());

        summarizerAgent.summarize("topic", "context", searchResult);

        verify(messageService).create(captor.capture());
        assertThat(captor.getValue().tools())
                .satisfies(tools -> assertThat(tools.orElse(List.of())).isEmpty());
    }

    @Test
    void should_useCorrectModelAndMaxTokens_when_buildingApiRequest() {
        stubApiCall("summary");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        SearchResult searchResult = new SearchResult("raw results", List.of());

        summarizerAgent.summarize("topic", "context", searchResult);

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.model()).isEqualTo(Model.CLAUDE_SONNET_4_6);
        assertThat(params.maxTokens()).isGreaterThanOrEqualTo(1024L);
    }

    @Test
    void should_returnEmpty_when_contentListIsEmpty() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of());
        SearchResult searchResult = new SearchResult("results", List.of());

        SummaryResult result = summarizerAgent.summarize("topic", "context", searchResult);

        assertThat(result.summary()).isEmpty();
        assertThat(result.sourceUrls()).isEmpty();
    }

    @Test
    void should_skipNonTextBlocks_when_contentBlockHasNoText() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.empty());
        SearchResult searchResult = new SearchResult("results", List.of());

        SummaryResult result = summarizerAgent.summarize("topic", "context", searchResult);

        assertThat(result.summary()).isEmpty();
    }

    @Test
    void should_concatenateAcrossBlocks_when_responseSpansMultipleContentBlocks() {
        ContentBlock secondBlock = mock(ContentBlock.class);
        TextBlock secondTextBlock = mock(TextBlock.class);

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock, secondBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("Part one.");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("Part two.");
        SearchResult searchResult = new SearchResult("results", List.of());

        SummaryResult result = summarizerAgent.summarize("topic", "context", searchResult);

        assertThat(result.summary()).contains("Part one.").contains("Part two.");
    }
}
