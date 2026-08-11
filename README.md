# tenahub-bot

Spring Boot Telegram bot for Ethiopia-focused pharmacy discovery: medicine search, pharmacy registration and inventory, reservations, ratings, favorites, SMS (Twilio), and geocoding.

## Stack

- Java 17+
- Spring Boot 4
- PostgreSQL + Spring Data JPA
- Telegram Bot API (webhook)
- Twilio SMS
- Google Open Location Code

## Run locally

1. Copy the example config and fill in secrets (the real `application.yml` is gitignored):

   ```bash
   cp src/main/resources/application.yml.example src/main/resources/application.yml
   ```

2. Start PostgreSQL and set `spring.datasource.*` in `application.yml` (or env vars).

3. Set Telegram and Twilio values (or `TELEGRAM_*` / `TWILIO_*` env vars).

4. Build and run:

   ```bash
   ./mvnw spring-boot:run
   ```

5. Point the Telegram webhook at `POST /telegram/webhook`.

## Tests

```bash
./mvnw test
```

The default test profile uses an in-memory H2 database and does not require live Telegram or Twilio credentials.
