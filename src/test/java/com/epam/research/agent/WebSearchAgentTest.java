package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUnion;
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
class WebSearchAgentTest {

    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messageService;
    @Mock private Message message;
    @Mock private ContentBlock contentBlock;
    @Mock private TextBlock textBlock;

    @InjectMocks
    private WebSearchAgent webSearchAgent;

    private void stubApiCall(String responseText) {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
    }

    @Test
    void should_returnSearchResults_when_validTopicAndContextProvided() {
        stubApiCall("Found relevant results about climate change research.");

        String result = webSearchAgent.search("climate change", "focus on 2020-2025, academic sources");

        assertThat(result).isEqualTo("Found relevant results about climate change research.");
    }

    @Test
    void should_includeWebSearchTool_when_buildingApiRequest() {
        stubApiCall("results");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        webSearchAgent.search("AI trends", "recent developments");

        verify(messageService).create(captor.capture());
        assertThat(captor.getValue().tools())
                .isPresent()
                .hasValueSatisfying(tools ->
                        assertThat(tools).anyMatch(ToolUnion::isWebSearchTool20250305));
    }

    @Test
    void should_includeTopicAndContextInUserMessage_when_buildingApiRequest() {
        stubApiCall("results");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        webSearchAgent.search("quantum computing", "focus on hardware advances, target engineers");

        verify(messageService).create(captor.capture());
        String userMessage = captor.getValue().messages().get(0).content().asString();
        assertThat(userMessage)
                .contains("quantum computing")
                .contains("focus on hardware advances, target engineers");
    }

    @Test
    void should_useCorrectModelAndMaxTokens_when_buildingApiRequest() {
        stubApiCall("results");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        webSearchAgent.search("topic", "context");

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

        String result = webSearchAgent.search("topic", "context");

        assertThat(result).isEmpty();
    }

    @Test
    void should_skipNonTextBlocks_when_contentBlockHasNoText() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.empty());

        String result = webSearchAgent.search("topic", "context");

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
        when(textBlock.text()).thenReturn("First part.");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("Second part.");

        String result = webSearchAgent.search("topic", "context");

        assertThat(result).contains("First part.").contains("Second part.");
    }

    @Test
    void should_requestSourceUrlsInSystemPrompt_when_buildingApiRequest() {
        stubApiCall("results with [1]\n\n## Sources\n[1] https://example.com");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        webSearchAgent.search("topic", "context");

        verify(messageService).create(captor.capture());
        String systemPrompt = captor.getValue().system()
                .flatMap(s -> s.string())
                .orElse("");
        assertThat(systemPrompt)
                .contains("## Sources")
                .contains("[N]");
    }

    @Test
    void should_returnResultsWithSourcesSection_when_responseIncludesCitations() {
        String responseWithSources = """
                Climate change is accelerating [1].

                ## Sources
                [1] https://ipcc.ch/report
                """;
        stubApiCall(responseWithSources);

        String result = webSearchAgent.search("climate change", "context");

        assertThat(result).contains("## Sources");
        assertThat(result).contains("[1] https://ipcc.ch/report");
    }
}
