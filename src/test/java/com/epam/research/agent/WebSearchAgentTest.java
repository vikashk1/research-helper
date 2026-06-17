package com.epam.research.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.epam.research.job.JobService;
import com.epam.research.agent.AgentResult;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

@ExtendWith(MockitoExtension.class)
class WebSearchAgentTest {

    private static final Long JOB_ID = 1L;

    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messageService;
    @Mock private Message message;
    @Mock private Usage usage;
    @Mock private ContentBlock contentBlock;
    @Mock private TextBlock textBlock;
    @Mock private JobService jobService;

    @InjectMocks
    private WebSearchAgent webSearchAgent;

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
        when(usage.inputTokens()).thenReturn(100L);
        when(usage.outputTokens()).thenReturn(50L);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
    }

    @Test
    void should_returnSearchResults_when_validTopicAndContextProvided() {
        stubApiCall("Found relevant results about climate change research.");

        AgentResult result = webSearchAgent.search(JOB_ID, "climate change", "focus on 2020-2025, academic sources");

        assertThat(result.content()).isEqualTo("Found relevant results about climate change research.");
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("SEARCH"), eq("start"), contains("climate change"));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("SEARCH"), eq("activity"), contains("Generating search queries for: climate change"));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("SEARCH"), eq("activity"), contains("Executing web search..."));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("SEARCH"), eq("activity"), contains("Web search results retrieved"));
        verify(jobService).appendStageEvent(eq(JOB_ID), eq("SEARCH"), eq("end"), contains("Web search complete"));
    }

    @Test
    void should_includeWebSearchTool_when_buildingApiRequest() {
        stubApiCall("results");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        webSearchAgent.search(JOB_ID, "AI trends", "recent developments");

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

        webSearchAgent.search(JOB_ID, "quantum computing", "focus on hardware advances, target engineers");

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

        webSearchAgent.search(JOB_ID, "topic", "context");

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.model()).isEqualTo(Model.CLAUDE_HAIKU_4_5);
        assertThat(params.maxTokens()).isGreaterThanOrEqualTo(2048L);
    }

    @Test
    void should_recordTokenUsage_when_searchCompletes() {
        stubApiCall("search result text");
        when(usage.inputTokens()).thenReturn(1200L);
        when(usage.outputTokens()).thenReturn(300L);

        AgentResult result = webSearchAgent.search(JOB_ID, "AI trends", "context");

        assertThat(result.inputTokens()).isEqualTo(1200L);
        assertThat(result.outputTokens()).isEqualTo(300L);
    }

    @Test
    void should_returnEmpty_when_contentListIsEmpty() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(0L);
        when(usage.outputTokens()).thenReturn(0L);
        when(message.content()).thenReturn(List.of());

        AgentResult result = webSearchAgent.search(JOB_ID, "topic", "context");

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

        AgentResult result = webSearchAgent.search(JOB_ID, "topic", "context");

        assertThat(result.content()).isEmpty();
    }

    @Test
    void should_concatenateAcrossBlocks_when_responseSpansMultipleContentBlocks() {
        ContentBlock secondBlock = mock(ContentBlock.class);
        TextBlock secondTextBlock = mock(TextBlock.class);

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.usage()).thenReturn(usage);
        when(usage.inputTokens()).thenReturn(100L);
        when(usage.outputTokens()).thenReturn(50L);
        when(message.content()).thenReturn(List.of(contentBlock, secondBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("First part.");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("Second part.");

        AgentResult result = webSearchAgent.search(JOB_ID, "topic", "context");

        assertThat(result.content()).contains("First part.").contains("Second part.");
    }
}
