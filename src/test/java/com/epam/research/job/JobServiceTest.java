package com.epam.research.job;

import com.epam.research.agent.ClarificationAgent;
import com.epam.research.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobLogRepository jobLogRepository;

    @Mock
    private JobStageRepository jobStageRepository;

    @Mock
    private ClarificationAgent clarificationAgent;

    @Mock
    private SseService sseService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private JobService jobService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobService, "modelId", "claude-haiku-4-5");
    }

    // --- getJob ---

    @Test
    void should_returnJob_when_jobExists() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        Job result = jobService.getJob(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void should_throwNotFoundException_when_jobNotFound() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- getAllJobs ---

    @Test
    void should_returnJobsOrderedByCreatedAtDesc() {
        Job job1 = new Job();
        Job job2 = new Job();
        when(jobRepository.findAll(any(Sort.class))).thenReturn(List.of(job1, job2));

        List<Job> result = jobService.getAllJobs();

        assertThat(result).containsExactly(job1, job2);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(jobRepository).findAll(sortCaptor.capture());
        Sort.Order order = sortCaptor.getValue().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // --- getClarificationQuestions ---

    @Test
    void should_delegateToClarificationAgent() {
        List<String> questions = List.of("Q1?", "Q2?");
        when(clarificationAgent.generateQuestions("AI trends")).thenReturn(questions);

        List<String> result = jobService.getClarificationQuestions("AI trends");

        assertThat(result).isEqualTo(questions);
        verify(clarificationAgent).generateQuestions("AI trends");
    }

    // --- appendLog ---

    @Test
    void should_persistJobLog_when_called() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        jobService.appendLog(1L, "Search started");

        ArgumentCaptor<JobLog> logCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getMessage()).isEqualTo("Search started");
        assertThat(logCaptor.getValue().getJob()).isEqualTo(job);
    }

    @Test
    void should_emitSseEvent_when_logAppended() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        jobService.appendLog(1L, "Search started");

        verify(sseService).emit(1L, "Search started");
    }

    @Test
    void should_throwNotFoundException_when_jobNotFoundOnAppendLog() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.appendLog(99L, "msg"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- restartJob ---

    @Test
    void should_resetJobToPendingAndClearReportAndError_when_restarted() {
        Job job = new Job();
        job.setId(1L);
        job.setStatus(JobStatus.FAILED);
        job.setReport("old report");
        job.setErrorMessage("some error");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job result = jobService.restartJob(1L);

        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getReport()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void should_deleteAllLogs_when_restarted() {
        Job job = new Job();
        job.setId(1L);
        job.setStatus(JobStatus.FAILED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.restartJob(1L);

        verify(jobLogRepository).deleteAllByJobId(1L);
        verify(jobStageRepository).deleteAllByJobId(1L);
    }

    @Test
    void should_throwNotFoundException_when_jobNotFoundOnRestart() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.restartJob(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- deleteJob ---

    @Test
    void should_deleteLogsAndStagesAndJob_when_deleteJobCalled() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        jobService.deleteJob(1L);

        verify(jobLogRepository).deleteAllByJobId(1L);
        verify(jobStageRepository).deleteAllByJobId(1L);
        verify(jobRepository).delete(job);
    }

    @Test
    void should_throwNotFoundException_when_jobNotFoundOnDelete() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.deleteJob(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- deleteAllCompleted ---

    @Test
    void should_deleteAllCompletedJobsAndTheirLogsAndStages() {
        Job job1 = new Job();
        job1.setId(1L);
        Job job2 = new Job();
        job2.setId(2L);
        when(jobRepository.findAllByStatus(JobStatus.COMPLETED)).thenReturn(List.of(job1, job2));

        int result = jobService.deleteAllCompleted();

        assertThat(result).isEqualTo(2);
        verify(jobLogRepository).deleteAllByJobId(1L);
        verify(jobLogRepository).deleteAllByJobId(2L);
        verify(jobStageRepository).deleteAllByJobId(1L);
        verify(jobStageRepository).deleteAllByJobId(2L);
        verify(jobRepository).deleteAll(List.of(job1, job2));
    }

    @Test
    void should_returnZero_when_noCompletedJobsExist() {
        when(jobRepository.findAllByStatus(JobStatus.COMPLETED)).thenReturn(List.of());

        int result = jobService.deleteAllCompleted();

        assertThat(result).isEqualTo(0);
        verify(jobRepository).deleteAll(List.of());
    }

    // --- createJob ---

    @Test
    void should_persistJobWithPendingStatus() {
        Map<String, String> answers = Map.of("Scope?", "Global");
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.createJob("climate change", answers);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void should_storeClarificationAnswers() {
        Map<String, String> answers = Map.of("Scope?", "Global");
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.createJob("climate change", answers);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getClarificationAnswers()).isEqualTo(answers);
        assertThat(jobCaptor.getValue().getTopic()).isEqualTo("climate change");
    }

    @Test
    void should_returnSavedJob() {
        Job savedJob = new Job();
        savedJob.setId(42L);
        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        Job result = jobService.createJob("AI trends", Map.of());

        assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    void should_setModelId_when_jobCreated() {
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.createJob("quantum computing", Map.of());

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getModelId()).isEqualTo("claude-haiku-4-5");
    }

    // --- getJobResponse ---

    @Test
    void should_returnJobResponseWithEmptyStages_when_noStagesExist() {
        Job job = new Job();
        job.setId(1L);
        job.setTopic("AI trends");
        job.setStatus(JobStatus.IN_PROGRESS);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findAllByJobIdOrderByIdAsc(1L)).thenReturn(List.of());

        JobResponseDto result = jobService.getJobResponse(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.topic()).isEqualTo("AI trends");
        assertThat(result.stages()).isEmpty();
    }

    @Test
    void should_returnJobResponseWithMappedStages_when_stagesExist() {
        Job job = new Job();
        job.setId(1L);
        job.setTopic("quantum");
        job.setStatus(JobStatus.COMPLETED);

        JobStage stage = new JobStage();
        stage.setStage(PipelineStage.SEARCH);
        stage.setStatus(JobStageStatus.COMPLETED);
        stage.setStartedAt(java.time.LocalDateTime.of(2025, 1, 1, 10, 0));
        stage.setEndedAt(java.time.LocalDateTime.of(2025, 1, 1, 10, 1));

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findAllByJobIdOrderByIdAsc(1L)).thenReturn(List.of(stage));

        JobResponseDto result = jobService.getJobResponse(1L);

        assertThat(result.stages()).hasSize(1);
        JobStageDto dto = result.stages().get(0);
        assertThat(dto.stage()).isEqualTo(PipelineStage.SEARCH);
        assertThat(dto.status()).isEqualTo(JobStageStatus.COMPLETED);
        assertThat(dto.startedAt()).isEqualTo(java.time.LocalDateTime.of(2025, 1, 1, 10, 0));
        assertThat(dto.endedAt()).isEqualTo(java.time.LocalDateTime.of(2025, 1, 1, 10, 1));
    }

    @Test
    void should_throwNotFoundException_when_jobNotFoundOnGetJobResponse() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJobResponse(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- addTokenUsage ---

    @Test
    void should_accumulateTokenCounts_when_addTokenUsageCalled() {
        Job job = new Job();
        job.setId(1L);
        job.setTotalInputTokens(50L);
        job.setTotalOutputTokens(20L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.addTokenUsage(1L, 100L, 200L);

        assertThat(job.getTotalInputTokens()).isEqualTo(150L);
        assertThat(job.getTotalOutputTokens()).isEqualTo(220L);
        verify(jobRepository).save(job);
    }

    @Test
    void should_throw_when_addTokenUsage_jobNotFound() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.addTokenUsage(99L, 10L, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    void should_resetTokenCounters_when_jobRestarted() {
        Job job = new Job();
        job.setId(1L);
        job.setStatus(JobStatus.FAILED);
        job.setTotalInputTokens(500L);
        job.setTotalOutputTokens(300L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job result = jobService.restartJob(1L);

        assertThat(result.getTotalInputTokens()).isEqualTo(0L);
        assertThat(result.getTotalOutputTokens()).isEqualTo(0L);
    }

    // --- appendStageEvent ---

    @Test
    void should_createJobStageWithActiveStatus_when_typeIsStart() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findByJobIdAndStage(1L, PipelineStage.SEARCH)).thenReturn(Optional.empty());
        when(jobStageRepository.save(any(JobStage.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.appendStageEvent(1L, "search", "start", "starting search");

        ArgumentCaptor<JobStage> captor = ArgumentCaptor.forClass(JobStage.class);
        verify(jobStageRepository).save(captor.capture());
        JobStage saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStageStatus.ACTIVE);
        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void should_emitStageEventWithElapsedZero_when_typeIsStart() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findByJobIdAndStage(1L, PipelineStage.SEARCH)).thenReturn(Optional.empty());
        when(jobStageRepository.save(any(JobStage.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.appendStageEvent(1L, "search", "start", "starting search");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(sseService).emitStage(eq(1L), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("\"elapsed\":0");
    }

    @Test
    void should_setCompletedStatusAndEndedAt_when_typeIsEnd() {
        Job job = new Job();
        job.setId(1L);
        JobStage existing = new JobStage();
        existing.setJob(job);
        existing.setStage(PipelineStage.SEARCH);
        existing.setStatus(JobStageStatus.ACTIVE);
        existing.setStartedAt(java.time.LocalDateTime.now().minusSeconds(2));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findByJobIdAndStage(1L, PipelineStage.SEARCH)).thenReturn(Optional.of(existing));
        when(jobStageRepository.save(any(JobStage.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.appendStageEvent(1L, "search", "end", "done");

        ArgumentCaptor<JobStage> captor = ArgumentCaptor.forClass(JobStage.class);
        verify(jobStageRepository).save(captor.capture());
        JobStage saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStageStatus.COMPLETED);
        assertThat(saved.getEndedAt()).isNotNull();
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(sseService).emitStage(eq(1L), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).doesNotContain("\"elapsed\":0");
    }

    @Test
    void should_notChangeStatusOrTimestamps_when_typeIsActivity() {
        Job job = new Job();
        job.setId(1L);
        JobStage existing = new JobStage();
        existing.setJob(job);
        existing.setStage(PipelineStage.SEARCH);
        existing.setStatus(JobStageStatus.ACTIVE);
        existing.setStartedAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobStageRepository.findByJobIdAndStage(1L, PipelineStage.SEARCH)).thenReturn(Optional.of(existing));
        when(jobStageRepository.save(any(JobStage.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.appendStageEvent(1L, "search", "activity", "still searching");

        ArgumentCaptor<JobStage> captor = ArgumentCaptor.forClass(JobStage.class);
        verify(jobStageRepository).save(captor.capture());
        JobStage saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStageStatus.ACTIVE);
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void should_throw400_when_unknownStageNameProvided() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.appendStageEvent(1L, "INVALID_STAGE", "start", "msg"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
