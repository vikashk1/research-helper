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
class ClarificationAgentTest {

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private MessageService messageService;

    @Mock
    private Message message;

    @Mock
    private ContentBlock contentBlock;

    @Mock
    private TextBlock textBlock;

    @InjectMocks
    private ClarificationAgent clarificationAgent;

    private void stubApiCall(String responseText) {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn(responseText);
    }

    @Test
    void should_returnThreeQuestions_when_validTopicProvided() {
        stubApiCall("What is the scope?\nWho is the target audience?\nWhat time period should be covered?");

        List<String> questions = clarificationAgent.generateQuestions("climate change");

        assertThat(questions)
                .hasSize(3)
                .containsExactly(
                        "What is the scope?",
                        "Who is the target audience?",
                        "What time period should be covered?"
                );
    }

    @Test
    void should_filterEmptyLines_when_responseContainsBlankLines() {
        stubApiCall("Question one?\n\nQuestion two?\n\nQuestion three?");

        List<String> questions = clarificationAgent.generateQuestions("AI trends");

        assertThat(questions)
                .hasSize(3)
                .containsExactly("Question one?", "Question two?", "Question three?");
    }

    @Test
    void should_trimWhitespace_when_responseHasLeadingTrailingSpaces() {
        stubApiCall("  What is the scope?  \n  Who is the audience?  ");

        List<String> questions = clarificationAgent.generateQuestions("research topic");

        assertThat(questions).containsExactly("What is the scope?", "Who is the audience?");
    }

    @Test
    void should_returnEmptyList_when_responseIsAllBlank() {
        stubApiCall("   \n\n   ");

        List<String> questions = clarificationAgent.generateQuestions("some topic");

        assertThat(questions).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_contentListIsEmpty() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of());

        List<String> questions = clarificationAgent.generateQuestions("some topic");

        assertThat(questions).isEmpty();
    }

    @Test
    void should_skipNonTextBlocks_when_contentBlockHasNoText() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(contentBlock.text()).thenReturn(Optional.empty());

        List<String> questions = clarificationAgent.generateQuestions("some topic");

        assertThat(questions).isEmpty();
    }

    @Test
    void should_concatenateAcrossBlocks_when_responseSpansMultipleContentBlocks() {
        ContentBlock secondBlock = mock(ContentBlock.class);
        TextBlock secondTextBlock = mock(TextBlock.class);

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(contentBlock, secondBlock));
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("Question one?");
        when(secondBlock.text()).thenReturn(Optional.of(secondTextBlock));
        when(secondTextBlock.text()).thenReturn("Question two?");

        List<String> questions = clarificationAgent.generateQuestions("topic");

        assertThat(questions).containsExactly("Question one?", "Question two?");
    }

    @Test
    void should_passCorrectModelAndMaxTokens_when_buildingApiRequest() {
        stubApiCall("Question one?\nQuestion two?");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        clarificationAgent.generateQuestions("machine learning");

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.maxTokens()).isEqualTo(512L);
        assertThat(params.model()).isEqualTo(Model.CLAUDE_HAIKU_4_5);
    }

    @Test
    void should_includeTopicInUserMessage_when_buildingApiRequest() {
        stubApiCall("Question?");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        clarificationAgent.generateQuestions("quantum computing");

        verify(messageService).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.messages()).hasSize(1);
        assertThat(params.messages().get(0).content().asString()).isEqualTo("quantum computing");
    }
}
