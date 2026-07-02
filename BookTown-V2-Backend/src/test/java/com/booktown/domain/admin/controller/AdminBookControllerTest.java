package com.booktown.domain.admin.controller;

import com.booktown.domain.admin.dto.ContentJobResponse;
import com.booktown.domain.admin.dto.RegisterBookResponse;
import com.booktown.domain.admin.entity.ContentJobStatus;
import com.booktown.domain.admin.service.AdminBookService;
import com.booktown.domain.book.service.BookCoverService;
import com.booktown.global.exception.CustomException;
import com.booktown.global.exception.ErrorCode;
import com.booktown.global.exception.GlobalExceptionHandler;
import com.booktown.global.observability.BooktownMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBookControllerTest {

    private MockMvc mockMvc;
    private AdminBookService adminBookService;
    private BookCoverService bookCoverService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adminBookService = mock(AdminBookService.class);
        bookCoverService = mock(BookCoverService.class);
        BooktownMetrics metrics = mock(BooktownMetrics.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminBookController(adminBookService, bookCoverService))
                .setControllerAdvice(new GlobalExceptionHandler(metrics))
                .build();
    }

    @Test
    void registerBook_returns_201_with_body() throws Exception {
        RegisterBookResponse response = new RegisterBookResponse(1L, "소나기", "황순원", "PROSE", "KOREA");
        when(adminBookService.registerBook(any())).thenReturn(response);

        mockMvc.perform(post("/admin/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "소나기", "author", "황순원", "genre", "PROSE", "country", "KOREA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bookId").value(1))
                .andExpect(jsonPath("$.data.title").value("소나기"));
    }

    @Test
    void uploadContent_returns_202_with_job_id() throws Exception {
        ContentJobResponse jobResponse = new ContentJobResponse(
                10L, 1L, ContentJobStatus.QUEUED.name(), null, null, false,
                LocalDateTime.now(), LocalDateTime.now());
        when(adminBookService.uploadContent(eq(1L), any())).thenReturn(jobResponse);

        MockMultipartFile file = new MockMultipartFile("file", "novel.txt", "text/plain", "내용".getBytes());

        mockMvc.perform(multipart("/admin/books/1/contents").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void uploadContent_returns_400_for_invalid_file_type() throws Exception {
        when(adminBookService.uploadContent(eq(1L), any()))
                .thenThrow(new CustomException(ErrorCode.INVALID_FILE_TYPE));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(multipart("/admin/books/1/contents").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadContent_returns_409_when_content_already_exists() throws Exception {
        when(adminBookService.uploadContent(eq(1L), any()))
                .thenThrow(new CustomException(ErrorCode.BOOK_CONTENT_ALREADY_EXISTS));

        MockMultipartFile file = new MockMultipartFile("file", "novel.txt", "text/plain", "내용".getBytes());

        mockMvc.perform(multipart("/admin/books/1/contents").file(file))
                .andExpect(status().isConflict());
    }

    @Test
    void getContentJob_returns_200_with_status() throws Exception {
        ContentJobResponse jobResponse = new ContentJobResponse(
                10L, 1L, ContentJobStatus.COMPLETED.name(), 5, null, false,
                LocalDateTime.now(), LocalDateTime.now());
        when(adminBookService.getContentJob(10L)).thenReturn(jobResponse);

        mockMvc.perform(get("/admin/content-jobs/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.chapterCount").value(5));
    }

    @Test
    void getContentJob_returns_404_when_not_found() throws Exception {
        when(adminBookService.getContentJob(99L))
                .thenThrow(new CustomException(ErrorCode.CONTENT_JOB_NOT_FOUND));

        mockMvc.perform(get("/admin/content-jobs/99"))
                .andExpect(status().isNotFound());
    }
}
