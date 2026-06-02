package com.ergowmr.taskmanager.tasks;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Component
public class UsersClient {

    private final RestClient client;

    public UsersClient(RestClient usersRestClient) {
        this.client = usersRestClient;
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findById(Long id) {
        return Optional.ofNullable(
            client.get()
                .uri("/users/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                .body(Map.class)
        );
    }
}
