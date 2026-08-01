package com.jloom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}