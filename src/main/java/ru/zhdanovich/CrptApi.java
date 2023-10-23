package ru.zhdanovich;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.concurrent.TimedSemaphore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final TimedSemaphore timedSemaphore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timedSemaphore = new TimedSemaphore(1, timeUnit, requestLimit);
    }

    /**
     * Метод который реализует единый метод создания документа.
     * В нем мы ограничеваем кол-во запросов к API при помощи TimedSemaphore.
     */
    public CreateDocumentResponse createDocument(HttpClient client, CreateDocumentRequest request) {
        try {
            timedSemaphore.acquire();

            HttpRequest httpRequest = HttpRequest
                    .newBuilder()
                    .uri(URI.create(ApplicationProperties.CREATE_DOCUMENT_URI + request.getPg()))
                    .header("content-type", "application/json")
                    .header("Authorization", "Bearer " + request.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), CreateDocumentResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Something in create document went wrong " + e);
        }
    }

    /**
     * Вспомогательный сервис, который отвечает за авторизацию.
     * Использовался для тестирования флоу создания документа.
     */
    static class AuthorizationService {
        private final HttpClient client;
        private final ObjectMapper objectMapper;

        AuthorizationService(HttpClient client, ObjectMapper objectMapper) {
            this.client = client;
            this.objectMapper = objectMapper;
        }

        public AuthorizationResponse authorization() {
            HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(ApplicationProperties.AUTHORIZATION_URI))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return objectMapper.readValue(response.body(), AuthorizationResponse.class);
            } catch (Exception e) {
                throw new RuntimeException("Something in authorization went wrong " + e);
            }
        }
    }

    /**
     * Вспомогательный класс который отвечает за получение токена.
     * Использовался для тестирования флоу создания документа.
     */
    static class TokenService {
        private final HttpClient client;
        private final ObjectMapper objectMapper;

        TokenService(HttpClient client, ObjectMapper objectMapper) {
            this.client = client;
            this.objectMapper = objectMapper;
        }

        public TokenResponse getAuthorizationToken(TokenRequest data) {
            try {
                HttpRequest request = HttpRequest
                        .newBuilder()
                        .uri(URI.create(ApplicationProperties.GET_TOKEN_URI))
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(data)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return objectMapper.readValue(response.body(), TokenResponse.class);
            } catch (Exception e) {
                throw new RuntimeException("Something in getToken went wrong " + e);
            }
        }
    }

    /**
     * Классы Dto
     */
    static class AuthorizationResponse {
        public String uuid;
        public String data;

        public String getUuid() {
            return uuid;
        }

        public String getData() {
            return data;
        }
    }

    static class TokenRequest {
        public String uuid;

        public String data;

        public TokenRequest(String uuid, String data) {
            this.uuid = uuid;
            this.data = data;
        }

        public String getUuid() {
            return uuid;
        }

        public String getData() {
            return data;
        }
    }

    static class TokenResponse {
        public String token;

        public String getToken() {
            return token;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class CreateDocumentRequest {
        @JsonIgnore
        private String token;

        @JsonIgnore
        private String pg;

        @JsonProperty("document_format")
        private DocumentFormat documentFormat;

        @JsonSerialize(using = ProductDocumentSerializer.class)
        @JsonProperty("product_document")
        private String productDocument;

        @JsonProperty("product_group")
        private ProductGroup productGroup;

        @JsonSerialize(using = SignatureDocumentSerializer.class)
        private String signature;

        private Type type;

        public CreateDocumentRequest(
                String token,
                String pg,
                DocumentFormat documentFormat,
                String productDocument,
                String signature,
                Type type
        ) {
            this.token = token;
            this.pg = pg;
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.signature = signature;
            this.type = type;
        }

        public CreateDocumentRequest(
                String token,
                String pg,
                DocumentFormat documentFormat,
                String productDocument,
                ProductGroup productGroup,
                String signature,
                Type type
        ) {
            this.token = token;
            this.pg = pg;
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.productGroup = productGroup;
            this.signature = signature;
            this.type = type;
        }

        public String getToken() {
            return token;
        }

        public String getPg() {
            return pg;
        }

        public DocumentFormat getDocumentFormat() {
            return documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public ProductGroup getProductGroup() {
            return productGroup;
        }

        public String getSignature() {
            return signature;
        }

        public Type getType() {
            return type;
        }

        static class ProductDocumentSerializer extends JsonSerializer<String> {
            @Override
            public void serialize(
                    String productDocument,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider
            ) throws IOException {
                jsonGenerator.writeObject(Base64.getEncoder().encodeToString(productDocument.getBytes()));
            }
        }

        static class SignatureDocumentSerializer extends JsonSerializer<String> {
            @Override
            public void serialize(
                    String signature,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider
            ) throws IOException {
                jsonGenerator.writeObject(Base64.getEncoder().encodeToString(signature.getBytes()));
            }
        }
    }

    static class CreateDocumentResponse {
        private String value;

        public String getValue() {
            return value;
        }
    }

    /**
     * Вспомогательные Enum'ы
     */
    enum DocumentFormat {
        // Реализовал только для JSON
        MANUAL, XML, CSV
    }

    enum ProductGroup {
        CLOTHES("clothes"),
        SHOES("shoes"),
        TOBACCO("tobacco"),
        PERFUMERY("perfumery"),
        TIRES("tires"),
        ELECTRONICS("electronics"),
        PHARMA("pharma"),
        MILK("milk"),
        BICYCLE("bicycle"),
        WHEELCHAIRS("wheelchairs");

        private final String value;

        ProductGroup(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Только этот тайп, так как нам кужно только ввод в оборот производство РФ
     */
    enum Type {
        LP_INTRODUCE_GOODS
    }

    /**
     * Вспомогательный класс для того что бы преобразовать один класс в другой
     * Использовался для тестирования флоу создания документа
     */
    static class Mapper {
        public TokenRequest map(AuthorizationResponse response) {
            return new TokenRequest(
                    response.uuid,
                    Base64.getEncoder().encodeToString(response.data.getBytes())
            );
        }
    }

    /**
     * класс для хранения пропертей приложения
     */
    static class ApplicationProperties {
        public static final String AUTHORIZATION_URI = "https://ismp.crpt.ru/api/v3/auth/cert/key";
        public static final String GET_TOKEN_URI = "https://ismp.crpt.ru/api/v3/auth/cert/";
        public static final String CREATE_DOCUMENT_URI = "https://ismp.crpt.ru/api/v3/lk/documents/create?pg=";
    }
}

