package com.ergowmr.taskmanager.users;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository repo;

    public UserController(UserRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    void seed() {
        if (repo.count() > 0) return;
        seedOne("alice",  "alice@example.com");
        seedOne("bob",    "bob@example.com");
        seedOne("younes", "younes@ergowmr.dev");
    }

    private void seedOne(String username, String email) {
        try {
            repo.save(new User(null, username, email));
        } catch (DataIntegrityViolationException ignored) {
            // Race avec un autre replica au démarrage — un autre pod a déjà inséré ce user.
        }
    }

    @GetMapping
    public List<User> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable Long id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@Valid @RequestBody User input) {
        User user = new User(null, input.getUsername(), input.getEmail());
        return repo.save(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
