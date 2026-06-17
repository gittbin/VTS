# VTS — Ứng dụng gọi video & thoại 1-1 (WebRTC)

VTS là ứng dụng gọi video/thoại trực tiếp 1-1 giữa hai người dùng, chạy hoàn toàn trên trình duyệt bằng **WebRTC**. Backend Spring Boot lo xác thực, danh sách người dùng, signaling qua WebSocket và lưu lịch sử cuộc gọi; còn media (âm thanh/hình ảnh) đi **thẳng peer-to-peer** giữa hai trình duyệt.

## Tính năng

- Đăng ký / đăng nhập bằng **JWT** (mật khẩu băm BCrypt).
- Danh sách người dùng kèm trạng thái **online/offline theo thời gian thực**.
- Gọi **video** và gọi **thoại** 1-1 (tắt/bật mic, tắt/bật camera, kết thúc cuộc gọi).
- **Lịch sử cuộc gọi**: gọi đi/đến, loại (video/thoại), trạng thái (hoàn thành / nhỡ / từ chối), thời lượng.

## Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Backend | Spring Boot 4.0.6 (Java 17, chạy tốt trên JDK 24) |
| Bảo mật | Spring Security 7 + JWT (jjwt 0.12.6), stateless |
| CSDL | MongoDB Atlas (cloud) |
| JSON | Jackson 3 — mặc định của Spring Boot 4, package `tools.jackson.*` |
| Realtime | WebSocket thuần (`TextWebSocketHandler`) cho signaling |
| Media | WebRTC (peer-to-peer) |
| Frontend | HTML + CSS + JavaScript thuần (không framework), phục vụ tĩnh từ `resources/static` |

## Yêu cầu

- **JDK 17+** (dự án đang chạy tốt trên JDK 24).
- **Maven** — đã kèm sẵn wrapper (`./mvnw`), không cần cài riêng.
- Một tài khoản **MongoDB Atlas** + connection string.
- Trình duyệt hiện đại (Chrome, Edge, Firefox…).

## Cài đặt & chạy

1. **Lấy connection string MongoDB Atlas** (dạng `mongodb+srv://user:pass@cluster.../db`).
2. **Đặt biến môi trường** `MONGODB_URI` và `JWT_SECRET` (xem mục Cấu hình).
3. *(Tùy chọn)* Tạo sẵn collection + index:
   ```bash
   mongosh "<MONGODB_URI>" scripts/mongo-init.js
   ```
   Bỏ qua cũng được — app đã bật `auto-index-creation`.
4. **Chạy app:**
   ```bash
   ./mvnw spring-boot:run
   ```
   hoặc chạy `VtsApplication` trong IDE.
5. Mở trình duyệt: **http://localhost:8080**

## Cấu hình (biến môi trường)

`application.properties` đọc 2 biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| `MONGODB_URI` | Connection string MongoDB Atlas |
| `JWT_SECRET` | Khóa ký JWT — **tối thiểu 32 ký tự** (HMAC-SHA256) |

Các thuộc tính liên quan:
```properties
spring.data.mongodb.uri=${MONGODB_URI}
spring.data.mongodb.auto-index-creation=true
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=86400000
```

**Cách đặt biến môi trường:**

- **IntelliJ**: Run → Edit Configurations → `VtsApplication` → *Environment variables* →
  `MONGODB_URI=mongodb+srv://...;JWT_SECRET=chuoi-bi-mat-toi-thieu-32-ky-tu`
- **PowerShell (Windows):**
  ```powershell
  $env:MONGODB_URI="mongodb+srv://..."
  $env:JWT_SECRET="chuoi-bi-mat-toi-thieu-32-ky-tu-abcdef"
  ./mvnw spring-boot:run
  ```
- **bash (macOS/Linux):**
  ```bash
  export MONGODB_URI="mongodb+srv://..."
  export JWT_SECRET="chuoi-bi-mat-toi-thieu-32-ky-tu-abcdef"
  ./mvnw spring-boot:run
  ```

## Cách dùng / Demo

1. Mở **http://localhost:8080** → tab **Đăng ký** → tạo tài khoản (đăng ký xong tự đăng nhập).
2. Mở **trình duyệt thứ hai** (hoặc cửa sổ ẩn danh), đăng ký/đăng nhập tài khoản thứ hai.
3. Ở trang chính, mục "Mọi người" hiện người kia với chấm xanh **Đang hoạt động**.
4. Bấm nút **gọi video** hoặc **gọi thoại** → bên kia thấy cửa sổ gọi đến → **Trả lời**.
5. Trong cuộc gọi: tắt/bật mic, tắt/bật camera, **Kết thúc**.
6. Gọi xong, mục **Lịch sử cuộc gọi** tự cập nhật.

## Lưu ý quan trọng khi test gọi video

Đây là những điểm hay vướng nhất với WebRTC:

1. **Camera/mic chỉ chạy ở "secure context"** — tức `http://localhost` **hoặc** HTTPS. Mở qua IP LAN trên HTTP (vd `http://192.168.x.x:8080`) sẽ **bị chặn camera/mic**.
2. **Test 2 thiết bị riêng là tốt nhất** (mỗi máy có cam/mic riêng). Khi gọi giữa 2 máy/qua Internet cần **HTTPS** — cách nhanh: `ngrok http 8080` rồi mở URL `https://…` trên cả hai máy.
3. **Một webcam vật lý không mở được bởi 2 trình duyệt cùng lúc** trên 1 máy. Muốn test 2 chiều trên 1 máy: dùng **OBS → Start Virtual Camera** cho trình duyệt thứ hai, hoặc test bằng nút gọi thoại.
4. **STUN không đủ cho mọi mạng.** NAT chặt (4G/CGNAT) cần **TURN** để relay media. Mặc định chỉ có STUN của Google trong `js/webrtc.js` → thêm TURN vào mảng `ICE_SERVERS` (đã có dòng chú thích sẵn).

## Cấu trúc thư mục

```
VTS/
├── pom.xml
├── scripts/
│   └── mongo-init.js                       # tạo collection + index (database script)
└── src/main/
    ├── java/com/project/vts/
    │   ├── VtsApplication.java
    │   ├── config/
    │   │   ├── SecurityConfig.java          # JWT stateless, phân quyền
    │   │   └── WebSocketConfig.java         # đăng ký endpoint /ws
    │   ├── controller/
    │   │   ├── AuthController.java           # /api/auth/**
    │   │   ├── UserController.java           # /api/users
    │   │   └── CallHistoryController.java    # /api/calls
    │   ├── dto/                              # request + response (đều là record)
    │   ├── exception/                        # BadRequestException, GlobalExceptionHandler
    │   ├── model/                            # User, CallHistory (@Document)
    │   ├── repository/                       # MongoRepository
    │   ├── security/                         # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
    │   ├── service/                          # AuthService, UserService, CallHistoryService
    │   └── signaling/
    │       ├── SessionRegistry.java          # userId -> WebSocketSession (NGUỒN CHÂN LÝ presence)
    │       ├── SignalingHandler.java         # relay offer/answer/ICE + presence + ghi lịch sử
    │       └── JwtHandshakeInterceptor.java  # xác thực token ở handshake
    └── resources/
        ├── application.properties
        └── static/
            ├── login.html
            ├── index.html                    # danh sách + overlay cuộc gọi + lịch sử
            └── js/
                ├── auth.js                   # quản lý JWT, apiFetch
                ├── signaling.js              # client WebSocket
                └── webrtc.js                 # engine gọi (RTCPeerConnection)
```

## API

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Đăng ký (trả JWT) | Không |
| POST | `/api/auth/login` | Đăng nhập (trả JWT) | Không |
| GET | `/api/users` | Danh sách mọi người (trừ mình) | Bearer |
| GET | `/api/users/me` | Thông tin của mình | Bearer |
| GET | `/api/calls` | Lịch sử cuộc gọi của mình | Bearer |
| WS | `/ws?token=<JWT>` | Kênh signaling (presence + offer/answer/ICE) | Token qua query |

## Kiến trúc & quyết định thiết kế

- **Luồng tổng thể:** đăng nhập (JWT) → tải danh sách user → mở WebSocket nhận presence → bấm gọi → trao đổi SDP/ICE qua WebSocket → media chạy thẳng P2P → kết thúc, server ghi lịch sử.
- **Nguồn chân lý của trạng thái online là RAM** (`SessionRegistry` — một `ConcurrentHashMap<userId, session>`), **không phải MongoDB**. Field `online`/`lastSeen` trong DB chỉ là "biết lần cuối" → tránh kẹt "online" khi server crash.
- **Signaling là relay điểm-điểm theo `userId`:** mọi bản tin có `to` được server đóng dấu `from` rồi chuyển thẳng tới đúng người. Dùng `TextWebSocketHandler` thuần (không STOMP) để định tuyến theo userId.
- **SDP/ICE không lưu DB** — chúng ephemeral, chỉ relay rồi bỏ.
- **Token WebSocket truyền qua query** (`/ws?token=…`) vì trình duyệt không set được header cho WebSocket; xác thực tại `JwtHandshakeInterceptor`.
- **Lịch sử cuộc gọi** ghi server-side bằng cách "nghe" các bản tin điều khiển (`call-request/accept/reject/cancel/end`); mỗi cuộc gọi có `callId` (client sinh) để khớp các bản tin của cùng một cuộc.
- **DTO dạng record** để mật khẩu không lọt ra response.

## Ghi chú kỹ thuật (gotchas)

- **Spring Boot 4 dùng Jackson 3** → import từ `tools.jackson.*` (không phải `com.fasterxml.jackson.*`); riêng annotation vẫn ở `com.fasterxml.jackson.annotation`.
- Cần dependency **`spring-boot-starter-websocket`** (không đi kèm `starter-web`).
- Thuộc tính Mongo đúng là **`spring.data.mongodb.uri`** (không phải `spring.mongodb.uri`).
- Sửa file trong `src/main/resources/static/` thì **phải restart app** (hoặc build lại) để phục vụ bản mới; trình duyệt nhớ **hard refresh (Ctrl+Shift+R)** để bỏ cache JS.

## Tính năng nâng cao (chưa làm — tùy chọn)

- **Chia sẻ màn hình** — `getDisplayMedia` + thay track (dễ nhất).
- **Ghi hình cuộc gọi** — `MediaRecorder` phía client.
- **Gọi nhóm** — mesh chỉ ~3–4 người; đông hơn cần SFU và sẽ phá vỡ mô hình thuần P2P.