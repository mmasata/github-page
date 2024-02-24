package com.example.demo.database;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DemoEntityRepository extends ReactiveCrudRepository<DemoEntity, UUID> {
}
