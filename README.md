
# URL Shortener Service

## Use Case
This service allows you to shorten URLs and redirect users using a short code.

**Example:**
- Shorten a URL:
  ```bash
  curl -i -X PUT 'http://localhost:8080/?short=abc&long=http://example.com'
  ```
- Redirect using short code:
  ```bash
  curl -i 'http://localhost:8080/abc'
  ```

## Running
1. Open a terminal and navigate to the `url_shortener` directory:
    ```bash
    cd url_shortener
    ```
2. Make the Maven wrapper executable (if needed):
    ```bash
    chmod +x mvnw
    ```
3. Start the Spring Boot application:
    ```bash
    ./mvnw spring-boot:run
    ```
4. The service will be available at `http://localhost:8080`.