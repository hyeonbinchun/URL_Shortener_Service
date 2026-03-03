# URL_Shortener_Service

## METHOD
PUT:
    curl -i -X PUT 'http://localhost:8080/?short=abc&long=http://example.com'
GET:
    curl -i 'http://localhost:8080/abc'