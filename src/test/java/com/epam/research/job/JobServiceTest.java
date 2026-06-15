package com.epam.research.job;

import com.epam.research.agent.ClarificationAgent;
import com.epam.research.sse.SseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @InjectMocks
    private JobService jobService;

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
    void should_deleteLogsAndJob_when_deleteJobCalled() {
        Job job = new Job();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        jobService.deleteJob(1L);

        verify(jobLogRepository).deleteAllByJobId(1L);
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
    void should_deleteAllCompletedJobsAndTheirLogs() {
        Job job1 = new Job();
        job1.setId(1L);
        Job job2 = new Job();
        job2.setId(2L);
        when(jobRepository.findAllByStatus(JobStatus.COMPLETED)).thenReturn(List.of(job1, job2));

        int result = jobService.deleteAllCompleted();

        assertThat(result).isEqualTo(2);
        verify(jobLogRepository).deleteAllByJobId(1L);
        verify(jobLogRepository).deleteAllByJobId(2L);
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
}
