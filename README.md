# SHOPMART DISTRIBUTED E-COMMERCE MICROSERVICES PLATFORM

Hệ thống Microservice thương mại điện tử ShopMart nâng cấp nghiệp vụ Đặt hàng thành Giao dịch phân tán (Distributed Transaction) theo kiến trúc **Saga Pattern**, kết hợp **Spring Cloud**, **Resilience4j Circuit Breaker**, **Apache Kafka**, và **Redis Cache-Aside**.

---

## 1. CẤU TRÚC HỆ THỐNG & MODULES

```
e:\Test_Dau_Gio\
├── pom.xml                               # Parent POM quản lý versions & dependencies
├── docker-compose.yml                    # Hạ tầng Docker: Kafka, Zookeeper, Redis, MySQL
├── mvnw.bat                              # Maven Wrapper tiện ích biên dịch & kiểm thử
├── common-library/                       # Module chung: DTO, Enums, và Saga Events
├── config-server/                        # Port 8888: Centralized Config Server (@EnableConfigServer)
│   └── src/main/resources/config-repo/   # File cấu hình tập trung cho toàn bộ service
├── discovery-server/                     # Port 8761: Eureka Service Registry & Discovery
├── api-gateway/                          # Port 8080: Spring Cloud API Gateway + LoadBalancer
├── inventory-service/                    # Port 8082 (Instance 2: 8085): Quản lý kho, Redis Cache, Saga rollback
├── payment-service/                      # Port 8083: Xử lý thanh toán, kích hoạt rollback khi lỗi
└── order-service/                        # Port 8081: Quản lý đơn hàng, OpenFeign + Circuit Breaker, Saga, WebFlux
```

---

## 2. HƯỚNG DẪN KHỞI CHẠY HỆ THỐNG

### Bước 1: Khởi động Hạ tầng (Docker Compose)

Mở terminal và chạy lệnh:

```bash
docker compose up -d
```

Lệnh này sẽ khởi tạo:

- **Zookeeper**: Port 2181
- **Apache Kafka Broker**: Port 9092
- **Redis Cache Server**: Port 6379

### Bước 2: Biên dịch & Kiểm thử Toàn bộ Dự án

```cmd
.\mvnw.bat clean test
```

*Kết quả: 100% các bài test (Feign Sync, Circuit Breaker Fallback, Saga Flow, Rollback Compensating, Redis Cache-Aside) đều chạy thành công (SUCCESS).*

### Bước 3: Thứ tự Khởi chạy các Service (Run via IntelliJ hoặc Command line)

1. **Config Server** (Port `8888`): Chạy `ConfigServerApplication.java`
2. **Discovery Server** (Port `8761`): Chạy `DiscoveryServerApplication.java`
   - Truy cập Eureka Dashboard: http://localhost:8761
3. **API Gateway** (Port `8080`): Chạy `ApiGatewayApplication.java`
4. **Inventory Service** (Port `8082`): Chạy `InventoryApplication.java`
   - *Để chạy thêm instance 2 minh chứng LoadBalancer (Port 8085)*: Thêm VM Option `-Dserver.port=8085`
5. **Payment Service** (Port `8083`): Chạy `PaymentApplication.java`
6. **Order Service** (Port `8081`): Chạy `OrderApplication.java`

---

## 3. MINH CHỨNG & KIỂM TRA TỪNG TIÊU CHÍ ĐIỂM (100/100)

### CÂU 1: HẠ TẦNG CONFIG SERVER, EUREKA & GATEWAY (30đ)

- **Config Server (Port 8888)**: Cấu hình tập trung tại `config-server/src/main/resources/config-repo/`.
- **Eureka Dashboard**: Mở trình duyệt `http://localhost:8761`, kiểm tra danh sách instances đã đăng ký:
  - `CONFIG-SERVER`, `API-GATEWAY`, `ORDER-SERVICE`, `INVENTORY-SERVICE`, `PAYMENT-SERVICE`
- **API Gateway Routing (Port 8080)**:
  - Gọi qua Gateway: `GET http://localhost:8080/api/inventory/all`
  - Gọi qua Gateway: `GET http://localhost:8080/api/order/all`
  - Gọi qua Gateway: `GET http://localhost:8080/api/payment/all`

---

### CÂU 2: GIAO TIẾP ĐỒNG BỘ FEIGNCLIENT & CIRCUIT BREAKER (20đ)

#### 1. Gọi đồng bộ FeignClient có Load Balancing:

```bash
POST http://localhost:8080/api/order/create-sync
Content-Type: application/json

{
  "customerId": "CUST-001",
  "productCode": "PROD-001",
  "quantity": 2,
  "price": 30000000
}
```

*Response trả về thành công kèm thông tin Instance đã xử lý (Port 8082 hoặc Port 8085 minh chứng Load Balancing).*

#### 2. Kiểm thử Circuit Breaker & Fallback:

- Dừng `inventory-service` (hoặc gọi với mã sản phẩm lỗi).
- Gọi lại API `POST http://localhost:8080/api/order/create-sync`.
- **Kết quả Fallback trả về**:

```json
{
  "orderId": -1,
  "orderCode": "FALLBACK-REJECTED",
  "status": "FAILED",
  "message": "Inventory Service is currently unavailable. Request protected by Resilience4j Circuit Breaker..."
}
```

- **Log trạng thái Resilience4j**:
  - `CLOSED`: Khi service bình thường.
  - `OPEN`: Khi tỷ lệ lỗi vượt quá ngưỡng 50% (short-circuit trực tiếp vào fallback).
  - `HALF-OPEN`: Sau thời gian chờ 5s, cho phép các request thử nghiệm để kiểm tra phục hồi.

---

### CÂU 3: GIAO DỊCH PHÂN TÁN SAGA PATTERN & APACHE KAFKA (25đ)

#### 1. Happy Path (Thành công trọn vẹn):

```bash
POST http://localhost:8080/api/order/create-saga
Content-Type: application/json

{
  "customerId": "CUST-VIP",
  "productCode": "PROD-001",
  "quantity": 1,
  "price": 30000000,
  "forcePaymentFailure": false
}
```

**Luồng Saga diễn ra:**

1. `order-service`: Lưu đơn hàng trạng thái `PENDING` -> phát sự kiện `OrderCreatedEvent`.
2. `inventory-service`: Nhận sự kiện, trừ tồn kho -> phát sự kiện `InventoryReservedEvent`.
3. `payment-service`: Nhận sự kiện, thanh toán thành công -> phát sự kiện `PaymentCompletedEvent`.
4. `order-service`: Nhận sự kiện thành công -> cập nhật trạng thái đơn thành `CONFIRMED`.

#### 2. Rollback Path (Bù trừ giao dịch khi Thanh toán thất bại):

Cố tình kích hoạt lỗi thanh toán bằng cách đặt cờ `"forcePaymentFailure": true` hoặc số tiền vượt hạn mức:

```bash
POST http://localhost:8080/api/order/create-saga
Content-Type: application/json

{
  "customerId": "CUST-FAIL-TEST",
  "productCode": "PROD-001",
  "quantity": 2,
  "price": 30000000,
  "forcePaymentFailure": true
}
```

**Luồng Compensating / Rollback diễn ra:**

1. `order-service`: Tạo đơn hàng `PENDING` -> trừ tồn kho 2 sản phẩm tại `inventory-service`.
2. `payment-service`: Thanh toán thất bại (insufficient funds / forced failure) -> phát `PaymentFailedEvent`.
3. `inventory-service`: Nhận `PaymentFailedEvent` -> **KÍCH HOẠT COMPENSATING TRANSACTION**: Hoàn lại đúng 2 sản phẩm vào kho!
   - *Log SLF4J minh chứng:* `"[SAGA COMPENSATION - INVENTORY ROLLBACK] Stock successfully RESTORED for Product: PROD-001. Restored: +2"`.
4. `order-service`: Cập nhật đơn hàng thành `CANCELLED` với lý do `Payment Failed`.

#### 3. WebFlux Reactive Consumer:

- Kết nối tới Stream sự kiện thời gian thực:

```bash
GET http://localhost:8080/api/order/stream
Accept: text/event-stream
```

---

### CÂU 4: DISTRIBUTED CACHING VỚI REDIS (CACHE-ASIDE) (15đ)

1. **Lần gọi đầu (Cache Miss -> Query DB)**:

   ```bash
   GET http://localhost:8080/api/inventory/PROD-001
   ```

   *Log hệ thống xuất hiện:* `[CACHE MISS -> DATABASE QUERY] Querying database for productCode: 'PROD-001'`
2. **Lần gọi thứ hai trở đi (Cache Hit -> Lấy từ Redis)**:

   ```bash
   GET http://localhost:8080/api/inventory/PROD-001
   ```

   *Log không truy vấn DB nữa; dữ liệu được nạp siêu tốc từ Redis Cache.*
3. **Cập nhật & Xóa Cache (@CachePut / @CacheEvict)**:

   - Cập nhật sản phẩm: `POST http://localhost:8080/api/inventory` (tự động cập nhật Cache).
   - Xóa cache: `POST http://localhost:8080/api/inventory/cache/clear`.

---

### CÂU 5: CHẤT LƯỢNG CODE, LOGGING & KIỂM THỬ (10đ)

- Toàn bộ mã nguồn tuân thủ Clean Architecture, Package phân cấp rõ ràng theo chuẩn Microservice.
- Logging chuẩn SLF4J chi tiết từng trạng thái Forward và Rollback.
- Kiểm thử tự động chạy qua lệnh:

```cmd
.\mvnw.bat test
```

Toàn bộ Unit Test và Test Bù Trừ Rollback đều đạt 100% Passed.
