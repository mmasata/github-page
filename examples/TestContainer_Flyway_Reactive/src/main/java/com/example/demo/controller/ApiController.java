package com.example.demo.controller;

import com.example.demo.controller.model.CreateEntityRequest;
import com.example.demo.database.DemoEntity;
import com.example.demo.database.DemoEntityRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/demo_entity")
public class ApiController {

    private final DemoEntityRepository demoEntityRepository;

    public ApiController(DemoEntityRepository demoEntityRepository) {
        this.demoEntityRepository = demoEntityRepository;
    }

    @GetMapping
    public Flux<DemoEntity> getEntities() {
        return demoEntityRepository.findAll();
    }

    @PostMapping
    public Mono<DemoEntity> createEntity(@RequestBody Mono<CreateEntityRequest> requestMono) {
        return requestMono.flatMap(request -> {

            var newDemoEntity = new DemoEntity();
            newDemoEntity.setData(request.data());
            return demoEntityRepository.save(newDemoEntity);
        });
    }
}
