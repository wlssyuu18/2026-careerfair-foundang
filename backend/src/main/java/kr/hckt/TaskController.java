package kr.hckt;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class TaskController {
    private final TaskService service;
    private final TaskStore store;
    private final Ollama ollama;
    TaskController(TaskService service, TaskStore store, Ollama ollama) { this.service = service; this.store = store; this.ollama = ollama; }
    record Selection(String candidateId) {}
    record Answer(String answer, boolean submitted) {}
    record Approval(String fingerprint) {}
    @GetMapping("/system") Ollama.Status system() { return ollama.status(); }
    @GetMapping("/tasks") List<Task> list(Principal user) { return store.list(id(user)); }
    @GetMapping("/tasks/{task}") Task get(Principal user, @PathVariable UUID task) { return store.get(id(user), task); }
    @PostMapping(value = "/tasks", consumes = "multipart/form-data") Task create(Principal user, @RequestParam(defaultValue = "") String text, @RequestPart(required = false) MultipartFile image) throws IOException {
        if (text.length() > 6000 || (text.isBlank() && (image == null || image.isEmpty()))) throw Accounts.bad("안내문 이미지 또는 6,000자 이내의 요청을 입력해 주세요.");
        byte[] bytes = null;
        if (image != null && !image.isEmpty()) {
            if (image.getSize() > 8 * 1024 * 1024) throw Accounts.bad("이미지는 8MB 이하여야 해요.");
            bytes = image.getBytes();
            try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw Accounts.bad("JPG 또는 PNG 이미지를 올려주세요.");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!List.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase()) || (long) reader.getWidth(0) * reader.getHeight(0) > 16000000L) throw Accounts.bad("1,600만 화소 이하의 JPG·PNG 이미지를 올려주세요.");
                } finally { reader.dispose(); }
            }
        }
        return service.create(id(user), text, bytes);
    }
    @PostMapping("/tasks/{task}/search") Task search(Principal user, @PathVariable UUID task, @RequestBody Task.Conditions conditions) { return service.search(id(user), task, conditions); }
    @PostMapping("/tasks/{task}/select") Task select(Principal user, @PathVariable UUID task, @RequestBody Selection input) { return service.select(id(user), task, input.candidateId()); }
    @PostMapping("/tasks/{task}/respond") Task respond(Principal user, @PathVariable UUID task, @RequestBody Answer input) { return service.respond(id(user), task, input.answer(), input.submitted()); }
    @PostMapping("/tasks/{task}/approve") Task approve(Principal user, @PathVariable UUID task, @RequestBody Approval input) { return service.approve(id(user), task, input.fingerprint()); }
    @PostMapping("/tasks/{task}/stop") Task stop(Principal user, @PathVariable UUID task) { return service.stop(id(user), task); }
    @DeleteMapping("/tasks") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void delete(Principal user) { service.delete(id(user)); }
    private UUID id(Principal user) { return UUID.fromString(user.getName()); }
}
