package com.epam.research.job;

import com.epam.research.agent.CoordinatorAgent;
import com.epam.research.sse.SseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private CoordinatorAgent coordinatorAgent;

    @MockBean
    private SseService sseService;

    @Test
    void should_returnQuestions_when_clarifyEndpointCalled() throws Exception {
        when(jobService.getClarificationQuestions("climate change"))
                .thenReturn(List.of("Q1?", "Q2?", "Q3?"));

        mockMvc.perform(post("/api/jobs/clarify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "climate change"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Q1?"))
                .andExpect(jsonPath("$[1]").value("Q2?"))
                .andExpect(jsonPath("$[2]").value("Q3?"));
    }

    @Test
    void should_createJobAndStartPipeline_when_createEndpointCalled() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTopic("climate change");
        job.setStatus(JobStatus.PENDING);

        when(jobService.createJob(eq("climate change"), any())).thenReturn(job);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "climate change", "clarificationAnswers": {"Q1?": "A1"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.topic").value("climate change"));

        verify(coordinatorAgent).runPipeline(eq(1L), eq("climate change"), any());
    }

    @Test
    void should_returnAllJobs_when_listEndpointCalled() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTopic("AI trends");
        job.setStatus(JobStatus.COMPLETED);

        when(jobService.getAllJobs()).thenReturn(List.of(job));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].topic").value("AI trends"));
    }

    @Test
    void should_returnJob_when_getByIdEndpointCalled() throws Exception {
        JobResponseDto dto = new JobResponseDto(
                1L, "quantum computing", null, JobStatus.COMPLETED,
                "# Quantum Report", null, null, null, List.of(), 0L, 0L);

        when(jobService.getJobResponse(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.report").value("# Quantum Report"))
                .andExpect(jsonPath("$.stages").isArray())
                .andExpect(jsonPath("$.stages").isEmpty());
    }

    @Test
    void should_returnStages_when_jobHasStages() throws Exception {
        JobStageDto stageDto = new JobStageDto(PipelineStage.SEARCH, JobStageStatus.COMPLETED, null, null);
        JobResponseDto dto = new JobResponseDto(
                1L, "quantum computing", null, JobStatus.COMPLETED,
                "# Report", null, null, null, List.of(stageDto), 0L, 0L);

        when(jobService.getJobResponse(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages").isArray())
                .andExpect(jsonPath("$.stages[0].stage").value("SEARCH"))
                .andExpect(jsonPath("$.stages[0].status").value("COMPLETED"));
    }

    @Test
    void should_return404_when_jobNotFound() throws Exception {
        when(jobService.getJobResponse(99L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Job not found: 99"));

        mockMvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_restartJobAndRePipelineAndReturn200_when_restartEndpointCalled() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTopic("climate change");
        job.setStatus(JobStatus.PENDING);

        when(jobService.restartJob(1L)).thenReturn(job);

        mockMvc.perform(post("/api/jobs/1/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(jobService).restartJob(1L);
        verify(coordinatorAgent).runPipeline(eq(1L), eq("climate change"), any());
    }

    @Test
    void should_return404_when_restartCalledForMissingJob() throws Exception {
        when(jobService.restartJob(99L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Job not found: 99"));

        mockMvc.perform(post("/api/jobs/99/restart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return204_when_deleteJobEndpointCalled() throws Exception {
        doNothing().when(jobService).deleteJob(1L);

        mockMvc.perform(delete("/api/jobs/1"))
                .andExpect(status().isNoContent());

        verify(jobService).deleteJob(1L);
    }

    @Test
    void should_return404_when_deleteCalledForMissingJob() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Job not found: 99"))
                .when(jobService).deleteJob(99L);

        mockMvc.perform(delete("/api/jobs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_returnDeletedCount_when_deleteCompletedEndpointCalled() throws Exception {
        when(jobService.deleteAllCompleted()).thenReturn(3);

        mockMvc.perform(delete("/api/jobs/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3));

        verify(jobService).deleteAllCompleted();
    }

    @Test
    void should_registerSseEmitter_when_streamEndpointCalled() throws Exception {
        when(sseService.register(1L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/jobs/1/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk());

        verify(sseService).register(1L);
    }
}
