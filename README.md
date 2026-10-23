# My First Spring Boot App

Spring Boot 4.1.1 / Java 21 / Maven practice calculator.

## Features

- HTML and JavaScript calculator using `fetch()`
- GET `/calculate?a=10&b=5&op=add` (also subtract, multiply, divide)
- GET `/add?a=10&b=20`, `/multiply?a=5&b=8`
- GET `/hello?name=Taro`, `/hello-world`, `/test`

## Run locally

Install JDK 21 and Maven (or use Eclipse's Maven support).
Run `mvn spring-boot:run`, then open http://localhost:8080/.

In Eclipse, import as **Existing Maven Projects** and run `MyfirstappApplication.java` as a Java application.

## Notes

This repository contains source code and Maven settings, without generated `target/` files.
