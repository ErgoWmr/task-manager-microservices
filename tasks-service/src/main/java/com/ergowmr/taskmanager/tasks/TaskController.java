package com.ergowmr.taskmanager.tasks;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskRepository repo;
    private final UsersClient usersClient;

    public TaskController(TaskRepository repo, UsersClient usersClient) {
        this.repo = repo;
        this.usersClient = usersClient;
    }

    @PostConstruct
    void seed() {
        if (repo.count() > 0) return;
        repo.save(new Task(null, "Setup Kubernetes",  "Installer minikube + kubectl",     Task.Status.DONE,  3L));
        repo.save(new Task(null, "Push Docker image", "Build + push ergowmr/users-service:1", Task.Status.DONE,  3L));
        repo.save(new Task(null, "Add gateway",       "Ingress NGINX devant les services", Task.Status.DOING, 3L));
        repo.save(new Task(null, "Add tasks-service", "2e microservice + appel inter-service", Task.Status.TODO,  1L));
        repo.save(new Task(null, "Add database",      "PostgreSQL palier 16/20",           Task.Status.DOING, 3L));
    }

    @GetMapping
    public List<Task> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable Long id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Endpoint qui démontre l'appel inter-service : enrichit la tâche avec l'assignee. */
    @GetMapping("/{id}/full")
    public ResponseEntity<Map<String, Object>> getFull(@PathVariable Long id) {
        return repo.findById(id).map(task -> {
            Map<String, Object> assignee = usersClient.findById(task.getAssigneeId()).orElse(null);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", task.getId());
            body.put("title", task.getTitle());
            body.put("description", task.getDescription());
            body.put("status", task.getStatus());
            body.put("assigneeId", task.getAssigneeId());
            body.put("assignee", assignee);
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody Task input) {
        Task task = new Task(null, input.getTitle(), input.getDescription(),
                             input.getStatus() != null ? input.getStatus() : Task.Status.TODO,
                             input.getAssigneeId());
        return repo.save(task);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
