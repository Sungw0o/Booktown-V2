package com.booktown;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
@MockitoBean(types = {VectorStore.class, EmbeddingModel.class})
class BooktownBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
