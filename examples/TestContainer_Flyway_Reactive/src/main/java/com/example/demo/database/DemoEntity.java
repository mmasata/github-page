package com.example.demo.database;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "demo_entity")
public class DemoEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("data")
    private String data;

    public Long getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
