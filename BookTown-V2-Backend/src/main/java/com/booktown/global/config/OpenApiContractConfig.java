package com.booktown.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class OpenApiContractConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String CONTRACT_ONLY = "계약 우선 API입니다. 실제 비즈니스 구현은 연결된 GitHub Issue에서 진행합니다.";

    @Bean
    public OpenApiCustomizer apiContractCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            Paths paths = openApi.getPaths();
            if (paths == null) {
                paths = new Paths();
                openApi.setPaths(paths);
            }
            addContractSchemas(components);
            addBookContracts(paths);
            addAdminContentContracts(paths);
            addSummaryContracts(paths);
            addSceneContracts(paths);
            addQuizContracts(paths);
        };
    }

    private void addBookContracts(Paths paths) {
        add(paths, "/books", PathItem.HttpMethod.GET,
                operation("Books", "도서 목록 조회", "장르, 국가, 정렬, 페이지네이션 조건으로 도서 목록을 조회합니다.", false, "BookPageResponse")
                        .addParametersItem(query("genre", "장르 코드", new StringSchema()))
                        .addParametersItem(query("country", "국가 코드", new StringSchema()))
                        .addParametersItem(query("sort", "정렬 조건(latest, title, author)", new StringSchema()))
                        .addParametersItem(query("page", "페이지 번호(0부터 시작)", int32()))
                        .addParametersItem(query("size", "페이지 크기", int32())));
        add(paths, "/books/search", PathItem.HttpMethod.GET,
                operation("Books", "도서 검색", "제목 또는 저자 기준으로 도서를 검색합니다.", false, "BookPageResponse")
                        .addParametersItem(query("keyword", "검색어", new StringSchema()))
                        .addParametersItem(query("page", "페이지 번호(0부터 시작)", int32()))
                        .addParametersItem(query("size", "페이지 크기", int32())));
        add(paths, "/books/{bookId}", PathItem.HttpMethod.GET,
                operation("Books", "도서 상세 조회", "도서 상세 정보, 챕터 목록, 사용 가능한 AI 기능을 조회합니다.", false, "BookDetailResponse")
                        .addParametersItem(path("bookId", "도서 ID")));
        add(paths, "/books/{bookId}/bookmark", PathItem.HttpMethod.POST,
                operation("Bookmarks", "도서 찜 추가", "도서를 내 찜 목록에 추가합니다. 이미 찜한 경우에도 성공으로 처리합니다.", true, "BookmarkResponse")
                        .addParametersItem(path("bookId", "도서 ID")));
        add(paths, "/books/{bookId}/bookmark", PathItem.HttpMethod.DELETE,
                operation("Bookmarks", "도서 찜 취소", "도서를 내 찜 목록에서 제거합니다. 이미 제거된 경우에도 성공으로 처리합니다.", true, "BookmarkResponse")
                        .addParametersItem(path("bookId", "도서 ID")));
        add(paths, "/users/me/bookmarks", PathItem.HttpMethod.GET,
                operation("Bookmarks", "내 찜 도서 목록 조회", "로그인 사용자의 찜 도서 목록을 페이지네이션으로 조회합니다.", true, "BookmarkPageResponse")
                        .addParametersItem(query("page", "페이지 번호(0부터 시작)", int32()))
                        .addParametersItem(query("size", "페이지 크기", int32())));
    }

    private void addAdminContentContracts(Paths paths) {
        add(paths, "/admin/books", PathItem.HttpMethod.POST,
                operation("Admin Books", "관리자 도서 등록", "관리자가 도서 메타데이터를 등록합니다.", true, "AdminBookResponse")
                        .requestBody(jsonBody("AdminBookCreateRequest")));
        add(paths, "/admin/books/{bookId}/contents", PathItem.HttpMethod.POST,
                operation("Admin Contents", "원문 TXT 업로드", "관리자가 원문 TXT 파일을 업로드하고 처리 Job을 생성합니다.", true, "ContentJobResponse")
                        .addParametersItem(path("bookId", "도서 ID"))
                        .requestBody(multipartContentBody()));
        add(paths, "/admin/content-jobs/{jobId}", PathItem.HttpMethod.GET,
                operation("Admin Contents", "원문 처리 Job 조회", "원문 업로드 후 챕터/chunk 처리 상태를 조회합니다.", true, "ContentJobResponse")
                        .addParametersItem(path("jobId", "Job ID")));
    }

    private void addSummaryContracts(Paths paths) {
        add(paths, "/books/{bookId}/summaries", PathItem.HttpMethod.POST,
                operation("Summaries", "AI 요약 생성 요청", "도서 또는 챕터 범위의 RAG 기반 요약 생성 Job을 요청합니다.", true, "SummaryJobResponse")
                        .addParametersItem(path("bookId", "도서 ID"))
                        .requestBody(jsonBody("SummaryCreateRequest")));
        add(paths, "/summary-jobs/{jobId}", PathItem.HttpMethod.GET,
                operation("Summaries", "요약 생성 Job 조회", "요약 생성 Job 상태와 결과 연결 정보를 조회합니다.", true, "SummaryJobResponse")
                        .addParametersItem(path("jobId", "Job ID")));
        add(paths, "/books/{bookId}/summaries", PathItem.HttpMethod.GET,
                operation("Summaries", "도서 요약 목록 조회", "도서에 생성된 요약 목록을 조회합니다.", true, "SummaryListResponse")
                        .addParametersItem(path("bookId", "도서 ID")));
        add(paths, "/summaries/{summaryId}", PathItem.HttpMethod.GET,
                operation("Summaries", "요약 상세 조회", "요약 본문과 생성 메타데이터를 조회합니다.", true, "SummaryResponse")
                        .addParametersItem(path("summaryId", "요약 ID")));
        add(paths, "/summaries/{summaryId}/regenerations", PathItem.HttpMethod.POST,
                operation("Summaries", "요약 재생성 요청", "기존 요약을 보존하면서 재생성 Job을 요청합니다.", true, "SummaryJobResponse")
                        .addParametersItem(path("summaryId", "요약 ID")));
        add(paths, "/summaries/{summaryId}/feedback", PathItem.HttpMethod.PUT,
                operation("Summaries", "요약 피드백 저장", "요약 품질에 대한 사용자 피드백을 저장합니다.", true, "SummaryFeedbackResponse")
                        .addParametersItem(path("summaryId", "요약 ID"))
                        .requestBody(jsonBody("SummaryFeedbackRequest")));
    }

    private void addSceneContracts(Paths paths) {
        add(paths, "/books/{bookId}/scenes", PathItem.HttpMethod.GET,
                operation("Scenes", "도서 장면 목록 조회", "도서에서 추출된 주요 장면 목록을 조회합니다.", true, "SceneListResponse")
                        .addParametersItem(path("bookId", "도서 ID"))
                        .addParametersItem(query("page", "페이지 번호(0부터 시작)", int32()))
                        .addParametersItem(query("size", "페이지 크기", int32())));
        add(paths, "/scenes/{sceneId}/illustrations", PathItem.HttpMethod.POST,
                operation("Illustrations", "장면 일러스트 생성 요청", "선택한 장면의 이미지 생성 Job을 요청합니다.", true, "IllustrationJobResponse")
                        .addParametersItem(path("sceneId", "장면 ID"))
                        .requestBody(jsonBody("IllustrationCreateRequest")));
        add(paths, "/illustration-jobs/{jobId}", PathItem.HttpMethod.GET,
                operation("Illustrations", "일러스트 생성 Job 조회", "이미지 생성 Job 상태와 결과 연결 정보를 조회합니다.", true, "IllustrationJobResponse")
                        .addParametersItem(path("jobId", "Job ID")));
        add(paths, "/illustrations/{illustrationId}/regenerations", PathItem.HttpMethod.POST,
                operation("Illustrations", "일러스트 재생성 요청", "기존 이미지를 보존하면서 재생성 Job을 요청합니다.", true, "IllustrationJobResponse")
                        .addParametersItem(path("illustrationId", "일러스트 ID")));
    }

    private void addQuizContracts(Paths paths) {
        add(paths, "/books/{bookId}/quizzes", PathItem.HttpMethod.POST,
                operation("Quizzes", "객관식 퀴즈 생성 요청", "도서 또는 챕터 범위의 객관식 퀴즈 생성 Job을 요청합니다.", true, "QuizJobResponse")
                        .addParametersItem(path("bookId", "도서 ID"))
                        .requestBody(jsonBody("QuizCreateRequest")));
        add(paths, "/quiz-jobs/{jobId}", PathItem.HttpMethod.GET,
                operation("Quizzes", "퀴즈 생성 Job 조회", "퀴즈 생성 Job 상태와 결과 연결 정보를 조회합니다.", true, "QuizJobResponse")
                        .addParametersItem(path("jobId", "Job ID")));
        add(paths, "/quizzes/{quizId}", PathItem.HttpMethod.GET,
                operation("Quizzes", "퀴즈 상세 조회", "문항과 선택지를 조회합니다. 정답과 해설은 제출 전 응답에 포함하지 않습니다.", true, "QuizResponse")
                        .addParametersItem(path("quizId", "퀴즈 ID")));
        add(paths, "/quizzes/{quizId}/submissions", PathItem.HttpMethod.POST,
                operation("Quizzes", "퀴즈 제출 및 채점", "사용자 답안을 제출하고 서버 채점 결과를 반환합니다.", true, "QuizSubmissionResponse")
                        .addParametersItem(path("quizId", "퀴즈 ID"))
                        .requestBody(jsonBody("QuizSubmissionRequest")));
        add(paths, "/users/me/quizzes", PathItem.HttpMethod.GET,
                operation("Quizzes", "내 퀴즈 히스토리 조회", "로그인 사용자의 퀴즈 생성/제출 히스토리를 조회합니다.", true, "UserQuizHistoryResponse")
                        .addParametersItem(query("page", "페이지 번호(0부터 시작)", int32()))
                        .addParametersItem(query("size", "페이지 크기", int32())));
    }

    private Operation operation(String tag, String summary, String description, boolean secured, String responseSchema) {
        Operation operation = new Operation()
                .addTagsItem(tag)
                .summary(summary)
                .description(description + "\n\n" + CONTRACT_ONLY)
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse(responseSchema))
                        .addApiResponse("400", errorResponse("요청 값이 올바르지 않습니다."))
                        .addApiResponse("401", errorResponse("인증이 필요합니다."))
                        .addApiResponse("403", errorResponse("접근 권한이 없습니다."))
                        .addApiResponse("404", errorResponse("요청한 리소스를 찾을 수 없습니다."))
                        .addApiResponse("409", errorResponse("요청 충돌이 발생했습니다."))
                        .addApiResponse("429", errorResponse("요청 한도를 초과했습니다."))
                        .addApiResponse("502", errorResponse("외부 AI 서비스 오류가 발생했습니다.")));
        if (secured) {
            operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
        }
        return operation;
    }

    private void add(Paths paths, String path, PathItem.HttpMethod method, Operation operation) {
        PathItem pathItem = paths.get(path);
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.addPathItem(path, pathItem);
        }
        pathItem.operation(method, operation);
    }

    private ApiResponse jsonResponse(String schemaName) {
        return new ApiResponse()
                .description("성공")
                .content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(ref(schemaName))));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(ref("ErrorResponse"))));
    }

    private RequestBody jsonBody(String schemaName) {
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(ref(schemaName))));
    }

    private RequestBody multipartContentBody() {
        Schema<?> schema = object()
                .addProperty("file", new StringSchema().format("binary").description("업로드할 TXT 원문 파일"))
                .addProperty("copyrightConfirmed", new BooleanSchema().description("저작권/운영 절차 확인 여부"));
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType(MediaType.MULTIPART_FORM_DATA_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(schema)));
    }

    private Parameter path(String name, String description) {
        return new Parameter().name(name).in("path").required(true).description(description).schema(int64());
    }

    private Parameter query(String name, String description, Schema<?> schema) {
        return new Parameter().name(name).in("query").required(false).description(description).schema(schema);
    }

    private Schema<?> ref(String schemaName) {
        return new Schema<>().$ref("#/components/schemas/" + schemaName);
    }

    private IntegerSchema int32() {
        return new IntegerSchema().format("int32");
    }

    private IntegerSchema int64() {
        return new IntegerSchema().format("int64");
    }

    private ObjectSchema object() {
        return new ObjectSchema();
    }

    private ArraySchema arrayOf(String schemaName) {
        return new ArraySchema().items(ref(schemaName));
    }

    private StringSchema stringEnum(String... values) {
        StringSchema schema = new StringSchema();
        schema.setEnum(List.of(values));
        return schema;
    }

    private void addContractSchemas(Components components) {
        components.addSchemas("ErrorResponse", object()
                .addProperty("error", object()
                        .addProperty("code", new StringSchema().example("INVALID_INPUT"))
                        .addProperty("message", new StringSchema().example("요청 값이 올바르지 않습니다."))
                        .addProperty("fieldErrors", new ArraySchema().items(object()
                                .addProperty("field", new StringSchema().example("title"))
                                .addProperty("message", new StringSchema().example("필수 값입니다.")))))
                .addProperty("traceId", new StringSchema().example("trace-1234")));
        components.addSchemas("PageMeta", object()
                .addProperty("page", int32().example(0))
                .addProperty("size", int32().example(20))
                .addProperty("totalElements", int64().example(125))
                .addProperty("totalPages", int32().example(7))
                .addProperty("hasNext", new BooleanSchema().example(true)));
        components.addSchemas("BookCard", object()
                .addProperty("bookId", int64().example(1))
                .addProperty("title", new StringSchema().example("데미안"))
                .addProperty("author", new StringSchema().example("헤르만 헤세"))
                .addProperty("genre", new StringSchema().example("NOVEL"))
                .addProperty("country", new StringSchema().example("DE"))
                .addProperty("coverImageUrl", new StringSchema().example("https://cdn.booktown.shop/books/1.jpg"))
                .addProperty("availableFeatures", new ArraySchema().items(new StringSchema()).example(List.of("SUMMARY", "SCENE", "QUIZ")))
                .addProperty("isBookmarked", new BooleanSchema().example(false)));
        components.addSchemas("BookPageResponse", object()
                .addProperty("data", arrayOf("BookCard"))
                .addProperty("meta", ref("PageMeta")));
        components.addSchemas("ChapterResponse", object()
                .addProperty("chapterId", int64().example(10))
                .addProperty("title", new StringSchema().example("제1장"))
                .addProperty("order", int32().example(1)));
        components.addSchemas("BookDetailResponse", object()
                .addProperty("bookId", int64().example(1))
                .addProperty("title", new StringSchema().example("데미안"))
                .addProperty("author", new StringSchema().example("헤르만 헤세"))
                .addProperty("description", new StringSchema().example("고전 문학 입문자를 위한 작품 설명"))
                .addProperty("chapters", arrayOf("ChapterResponse"))
                .addProperty("availableFeatures", new ArraySchema().items(new StringSchema()).example(List.of("SUMMARY", "SCENE", "QUIZ")))
                .addProperty("isBookmarked", new BooleanSchema().example(false)));
        components.addSchemas("BookmarkResponse", object()
                .addProperty("bookId", int64().example(1))
                .addProperty("bookmarked", new BooleanSchema().example(true)));
        components.addSchemas("BookmarkPageResponse", object()
                .addProperty("data", arrayOf("BookCard"))
                .addProperty("meta", ref("PageMeta")));

        components.addSchemas("AdminBookCreateRequest", object()
                .addProperty("title", new StringSchema().example("데미안"))
                .addProperty("author", new StringSchema().example("헤르만 헤세"))
                .addProperty("genre", new StringSchema().example("NOVEL"))
                .addProperty("country", new StringSchema().example("DE"))
                .addProperty("description", new StringSchema().example("도서 설명"))
                .addProperty("coverImageUrl", new StringSchema().example("https://cdn.booktown.shop/books/1.jpg")));
        components.addSchemas("AdminBookResponse", object()
                .addProperty("bookId", int64().example(1))
                .addProperty("status", stringEnum("DRAFT", "PUBLISHED").example("DRAFT")));
        components.addSchemas("JobStatus", stringEnum("QUEUED", "PROCESSING", "COMPLETED", "FAILED"));
        components.addSchemas("ContentJobResponse", jobSchema("contentJobId", "챕터/chunk 처리 상태"));
        components.addSchemas("SummaryJobResponse", jobSchema("summaryJobId", "요약 생성 상태"));
        components.addSchemas("IllustrationJobResponse", jobSchema("illustrationJobId", "일러스트 생성 상태"));
        components.addSchemas("QuizJobResponse", jobSchema("quizJobId", "퀴즈 생성 상태"));

        components.addSchemas("SummaryCreateRequest", object()
                .addProperty("chapterIds", new ArraySchema().items(int64()).description("요약 대상 챕터 ID 목록"))
                .addProperty("tone", stringEnum("BASIC", "EASY", "DEEP").example("EASY")));
        components.addSchemas("SummaryResponse", object()
                .addProperty("summaryId", int64().example(200))
                .addProperty("bookId", int64().example(1))
                .addProperty("title", new StringSchema().example("데미안 1장 요약"))
                .addProperty("content", new StringSchema().example("요약 본문"))
                .addProperty("sourceChapterIds", new ArraySchema().items(int64()).example(List.of(10, 11))));
        components.addSchemas("SummaryListResponse", object()
                .addProperty("data", arrayOf("SummaryResponse"))
                .addProperty("meta", ref("PageMeta")));
        components.addSchemas("SummaryFeedbackRequest", object()
                .addProperty("rating", int32().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(5)).example(5))
                .addProperty("comment", new StringSchema().example("이해하기 쉬웠어요.")));
        components.addSchemas("SummaryFeedbackResponse", object()
                .addProperty("summaryId", int64().example(200))
                .addProperty("saved", new BooleanSchema().example(true)));

        components.addSchemas("SceneResponse", object()
                .addProperty("sceneId", int64().example(300))
                .addProperty("bookId", int64().example(1))
                .addProperty("chapterId", int64().example(10))
                .addProperty("title", new StringSchema().example("싱클레어의 고백"))
                .addProperty("excerpt", new StringSchema().example("장면을 대표하는 원문 일부")));
        components.addSchemas("SceneListResponse", object()
                .addProperty("data", arrayOf("SceneResponse"))
                .addProperty("meta", ref("PageMeta")));
        components.addSchemas("IllustrationCreateRequest", object()
                .addProperty("style", stringEnum("WATERCOLOR", "INK", "CLASSIC", "CINEMATIC").example("CLASSIC"))
                .addProperty("promptHint", new StringSchema().example("차분하고 고전적인 분위기")));
        components.addSchemas("IllustrationResponse", object()
                .addProperty("illustrationId", int64().example(400))
                .addProperty("sceneId", int64().example(300))
                .addProperty("imageUrl", new StringSchema().example("https://cdn.booktown.shop/illustrations/400.png")));

        components.addSchemas("QuizCreateRequest", object()
                .addProperty("chapterIds", new ArraySchema().items(int64()).description("퀴즈 대상 챕터 ID 목록"))
                .addProperty("questionCount", int32().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(20)).example(5))
                .addProperty("difficulty", stringEnum("EASY", "NORMAL", "HARD").example("NORMAL")));
        components.addSchemas("QuizOptionResponse", object()
                .addProperty("optionId", int64().example(1))
                .addProperty("content", new StringSchema().example("선택지 내용")));
        components.addSchemas("QuizQuestionResponse", object()
                .addProperty("questionId", int64().example(1))
                .addProperty("content", new StringSchema().example("다음 중 주인공의 심리와 가장 가까운 것은?"))
                .addProperty("options", arrayOf("QuizOptionResponse")));
        components.addSchemas("QuizResponse", object()
                .addProperty("quizId", int64().example(500))
                .addProperty("bookId", int64().example(1))
                .addProperty("questions", arrayOf("QuizQuestionResponse")));
        components.addSchemas("QuizAnswerRequest", object()
                .addProperty("questionId", int64().example(1))
                .addProperty("optionId", int64().example(3)));
        components.addSchemas("QuizSubmissionRequest", object()
                .addProperty("answers", arrayOf("QuizAnswerRequest")));
        components.addSchemas("QuizSubmissionResponse", object()
                .addProperty("quizId", int64().example(500))
                .addProperty("score", int32().example(80))
                .addProperty("correctCount", int32().example(4))
                .addProperty("totalCount", int32().example(5)));
        components.addSchemas("UserQuizHistoryResponse", object()
                .addProperty("data", arrayOf("QuizSubmissionResponse"))
                .addProperty("meta", ref("PageMeta")));
    }

    private Schema<?> jobSchema(String idField, String description) {
        return object()
                .description(description)
                .addProperty(idField, int64().example(100))
                .addProperty("status", ref("JobStatus"))
                .addProperty("resultId", int64().nullable(true).example(200))
                .addProperty("failureReason", new StringSchema().nullable(true).example(null));
    }
}
