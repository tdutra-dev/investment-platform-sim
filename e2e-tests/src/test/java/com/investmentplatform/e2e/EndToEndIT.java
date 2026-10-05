package com.investmentplatform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end flow over real infrastructure (MySQL, Kafka, MongoDB in Testcontainers)
 * and the three real, packaged services started as separate JVM processes:
 * POST /api/customers and POST /api/transactions -> outbox -> Kafka -> audit-log -> MongoDB.
 *
 * Runs in the {@code verify} phase (failsafe), after the service jars are packaged.
 */
@Testcontainers(disabledWithoutDocker = true)
class EndToEndIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("platform").withUsername("root").withPassword("root");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:25.0.6")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "keycloak", "investment-realm.json").toAbsolutePath().normalize()),
                    "/opt/keycloak/data/import/investment-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/investment").forPort(8080).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<Process> processes = new ArrayList<>();

    private static int customerPort;
    private static int transactionPort;
    private static int auditPort;
    private static String issuer;
    private static String token;

    @BeforeAll
    static void startServices() throws Exception {
        customerPort = freePort();
        transactionPort = freePort();
        auditPort = freePort();

        issuer = "http://localhost:" + keycloak.getMappedPort(8080) + "/realms/investment";
        token = fetchToken("customers:write transactions:write audit:read");
        String jdbcUrl = mysql.getJdbcUrl();
        String[][] sqlServices = {
                {"customer-service", String.valueOf(customerPort)},
                {"transaction-service", String.valueOf(transactionPort)}};
        for (String[] svc : sqlServices) {
            start(svc[0], svc[1],
                    "--spring.datasource.url=" + jdbcUrl,
                    "--spring.datasource.username=root",
                    "--spring.datasource.password=root",
                    "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "--outbox.publisher.fixed-delay-ms=500",
                    "--security.jwt.issuer=" + issuer,
                    "--security.jwt.jwk-set-uri=" + issuer + "/protocol/openid-connect/certs");
        }
        start("audit-log-service", String.valueOf(auditPort),
                "--spring.data.mongodb.uri=" + mongo.getReplicaSetUrl("auditlog"),
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--security.jwt.issuer=" + issuer,
                "--security.jwt.jwk-set-uri=" + issuer + "/protocol/openid-connect/certs");

        for (int port : new int[]{customerPort, transactionPort, auditPort}) {
            await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1))
                    .until(() -> isUp(port));
        }
    }

    @AfterAll
    static void stopServices() {
        processes.forEach(Process::destroy);
    }

    @Test
    void customerAndTransaction_shouldEndUpInMongoAuditLog() throws Exception {
        JsonNode customer = post(customerPort, "/api/customers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}");
        String customerId = customer.get("id").asText();

        JsonNode tx = post(transactionPort, "/api/transactions",
                "{\"customerId\":\"" + customerId + "\",\"amount\":250.00,\"currency\":\"EUR\",\"type\":\"DEPOSIT\"}");
        String transactionId = tx.get("id").asText();

        try (MongoClient client = MongoClients.create(mongo.getReplicaSetUrl("auditlog"))) {
            var collection = client.getDatabase("auditlog").getCollection("audit_log");

            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                Document txEvent = collection.find(new Document("aggregateId", transactionId)).first();
                assertThat(txEvent).isNotNull();
                assertThat(txEvent.getString("eventType")).isEqualTo("TransactionCreated");
                assertThat(txEvent.getString("topic")).isEqualTo("transaction-events");

                Document customerEvent = collection.find(new Document("aggregateId", customerId)).first();
                assertThat(customerEvent).isNotNull();
                assertThat(customerEvent.getString("eventType")).isEqualTo("CustomerRegistered");
                assertThat(customerEvent.getString("topic")).isEqualTo("customer-events");
            });
        }

        JsonNode entries = get(auditPort, "/api/audit-log?aggregateId=" + transactionId);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("eventType").asText()).isEqualTo("TransactionCreated");
    }

    @Test
    void requestsWithoutTokenOrScope_areRejected() throws Exception {
        String body = "{\"name\":\"Eve\",\"email\":\"eve@example.com\"}";
        assertThat(send(customerPort, "/api/customers", body, null)).isEqualTo(401);
        assertThat(send(customerPort, "/api/customers", body, fetchToken("audit:read"))).isEqualTo(403);
    }

    private static int send(int port, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String fetchToken(String scope) throws Exception {
        String form = "grant_type=client_credentials&client_id=platform-service-client"
                + "&client_secret=platform-service-secret&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).get("access_token").asText();
    }

    private static void start(String module, String port, String... args) throws IOException {
        Path jar = Path.of("..", module, "target", module + "-0.0.1-SNAPSHOT.jar").toAbsolutePath().normalize();
        assertThat(jar).as("run through the reactor (mvn verify) so the service jars are packaged").exists();

        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString(), "--server.port=" + port,
                "--spring.jpa.show-sql=false"));
        cmd.addAll(List.of(args));

        Path log = Path.of("target", module + ".log");
        Files.createDirectories(log.getParent());
        processes.add(new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start());
    }

    private static boolean isUp(int port) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/audit-log")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonNode post(int port, String path, String body) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return JSON.readTree(response.body());
    }

    private static JsonNode get(int port, String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
