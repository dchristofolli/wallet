package com.dchristofolli.wallet.controller;

import com.dchristofolli.wallet.dto.CreateWalletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Sql(statements = "DELETE FROM wallets", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WalletControllerIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("wallet_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createWallet_shouldReturn201WhenValidRequest() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("João Silva", "joao@email.com");

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(notNullValue())))
                .andExpect(jsonPath("$.ownerName", is("João Silva")))
                .andExpect(jsonPath("$.ownerEmail", is("joao@email.com")))
                .andExpect(jsonPath("$.balance", is(0)))
                .andExpect(jsonPath("$.createdAt", is(notNullValue())))
                .andExpect(jsonPath("$.updatedAt", is(notNullValue())));
    }

    @Test
    void createWallet_shouldReturn409WhenEmailDuplicate() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("João Silva", "joao@email.com");

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createWallet_shouldReturn400WhenOwnerNameTooShort() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("Jo", "joao@email.com");

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWallet_shouldReturn400WhenInvalidEmail() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("João Silva", "not-an-email");

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWallet_shouldReturn200WhenWalletExists() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("Maria Santos", "maria@email.com");

        String response = mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String walletId = extractIdFromJson(response);

        mockMvc.perform(get("/wallets/{walletId}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(walletId)))
                .andExpect(jsonPath("$.ownerName", is("Maria Santos")))
                .andExpect(jsonPath("$.ownerEmail", is("maria@email.com")));
    }

    @Test
    void getWallet_shouldReturn404WhenWalletNotFound() throws Exception {
        String nonExistentId = "00000000-0000-0000-0000-000000000000";

        mockMvc.perform(get("/wallets/{walletId}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    private static String asJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractIdFromJson(String json) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            return node.get("id").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
