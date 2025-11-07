# certwatch-springboot (commented & layered)

Spring Boot(embedded Tomcat) 기반 TLS 인증서 만료 점검 서비스.  
**주석을 촘촘히** 달아 어떤 코드가 무슨 일을 하는지 한 줄씩 이해할 수 있게 했습니다.  
패키º지 구조도 `config / service / web / schedule / model` 로 분리했습니다.

## Build
```bash
mvn -q -DskipTests packageº
```

## Run
```bash
# local profile (scheduler OFF)
java -jar target/certwatch-springboot-0.2.0.jar --spring.profiles.active=local

# dev profile (scheduler ON)
java -jar target/certwatch-springboot-0.2.0.jar --spring.profiles.active=dev

# prod profile (scheduler ON + Telegram required)
BOT_TOKEN=123:ABC CHAT_ID=123456 java -jar target/certwatch-springboot-0.2.0.jar --spring.profiles.active=prod
```

## API
- `POST /api/check` : 즉시 점검 실행(설정된 타깃 전부), JSON 결과 반환 + (조건부 텔레그램)

## Properties (application.properties)
- `certwatch.targets` : `host` or `host:port`, comma-separated
- `certwatch.targets-file` : line-separated file path (상대/절대 모두 가능)
- `certwatch.threshold-days` : alert 임계치(일)
- `certwatch.timeout-seconds` : per-host timeout(초)
- `certwatch.workers` : 동시 체크 스레드 수
- `certwatch.telegram.token`, `certwatch.telegram.chat-id`
- `certwatch.scheduling.enabled` : 스케줄 on/off
- `certwatch.scheduling.cron` : cron 식
