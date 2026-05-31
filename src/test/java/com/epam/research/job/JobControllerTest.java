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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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
        Job job = new Job();
        job.setId(1L);
        job.setTopic("quantum computing");
        job.setStatus(JobStatus.COMPLETED);
        job.setReport("# Quantum Report");

        when(jobService.getJob(1L)).thenReturn(job);

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.report").value("# Quantum Report"));
    }

    @Test
    void should_return404_when_jobNotFound() throws Exception {
        when(jobService.getJob(99L))
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
    void should_registerSseEmitter_when_streamEndpointCalled() throws Exception {
        when(sseService.register(1L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/jobs/1/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk());

        verify(sseService).register(1L);
    }
}
