# VTS — Ứng dụng gọi video & thoại (WebRTC)

VTS là ứng dụng gọi video/thoại trên trình duyệt bằng **WebRTC** theo mô hình **chỉ-bạn-bè (friends-only)**: gọi **1-1** hoặc **nhóm (tối đa 4 người, full-mesh)**. Backend Spring Boot lo xác thực, kết bạn, signaling qua WebSocket và lưu lịch sử cuộc gọi; còn media (âm thanh/hình ảnh) đi **thẳng peer-to-peer** giữa các trình duyệt.

## Tính năng

- Đăng ký / đăng nhập bằng **JWT** (mật khẩu băm BCrypt).
- **Kết bạn (friends-only)**: gửi/chấp nhận/từ chối/huỷ lời mời, huỷ kết bạn. **Chỉ thấy & chỉ gọi được bạn bè** — tìm người khác bằng **username chính xác** (không liệt kê toàn bộ user). Quyền được enforce ở cả REST lẫn signaling.
- Trạng thái **online/offline theo thời gian thực**, **chỉ của bạn bè**.
- Gọi **video** và gọi **thoại** 1-1 (tắt/bật mic, tắt/bật camera, kết thúc cuộc gọi).
- **Gọi nhóm (tối đa 4 người)** theo mô hình **full-mesh P2P**: chọn nhiều bạn → "Gọi nhóm" → lưới video nhiều người, tắt/bật mic-cam, rời nhóm.
- **Chia sẻ màn hình** trong cuộc gọi video (`getDisplayMedia` + thay track, không cần renegotiate).
- **Ghi cuộc gọi** phía client (`MediaRecorder`): video ghép màn hình đối phương + camera mình (PiP) và âm thanh 2 chiều; thoại thì ghi audio — bấm dừng là tự tải file `.webm` về.
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

## Cơ sở dữ liệu (MongoDB)

Dữ liệu nằm trong **3 collection**. App đã bật `auto-index-creation` nên index tự tạo từ annotation `@Indexed`/`@Query`; script `scripts/mongo-init.js` dùng để **tạo thủ công / bàn giao** (idempotent — chạy lại không sao).

| Collection | Vai trò | Index |
|---|---|---|
| `users` | Tài khoản người dùng (mật khẩu băm BCrypt, không bao giờ lưu plaintext) | `username` *(unique)*, `email` *(unique, sparse)* |
| `call_history` | Lịch sử cuộc gọi **1-1** (RINGING/ONGOING/COMPLETED/MISSED/REJECTED) | `callerId`, `calleeId`, `createdAt` *(desc)* |
| `friendships` | Quan hệ bạn bè (`PENDING` \| `ACCEPTED`) | `pairKey` *(unique)*, `requesterId`, `addresseeId`, `(requesterId,status)`, `(addresseeId,status)` |

- **`friendships.pairKey`** = hai `userId` sắp xếp tăng dần rồi nối bằng `_` (vd `<idA>_<idB>`). Ràng buộc **unique** ⇒ mỗi cặp người dùng chỉ có **một** bản ghi ở mọi hướng → chống trùng + chống race khi hai người gửi lời mời cho nhau cùng lúc (bản ghi thứ hai đụng khoá → xử lý auto-accept).
- **SDP/ICE và "phòng" gọi nhóm KHÔNG lưu DB** — chúng ephemeral, chỉ giữ trong RAM ở tầng signaling rồi bỏ.

**Chạy script tạo collection + index:**

```bash
mongosh "<MONGODB_URI>" scripts/mongo-init.js
```

> Script mở DB bằng `db.getSiblingDB('vts')` — đổi `'vts'` cho khớp tên database trong connection string của bạn. Bỏ qua bước này cũng được vì app tự tạo index khi khởi động.

## Cách dùng / Demo

1. Mở **http://localhost:8080** → tab **Đăng ký** → tạo tài khoản (đăng ký xong tự đăng nhập).
2. Mở **trình duyệt thứ hai** (hoặc cửa sổ ẩn danh), đăng ký/đăng nhập tài khoản thứ hai.
3. Mục **Tìm & thêm bạn**: gõ username (hoặc tên hiển thị) của người kia → **Kết bạn**; bên kia vào mục **Lời mời kết bạn** → **Chấp nhận**.
4. Hai người giờ thấy nhau ở mục **Bạn bè** với chấm xanh **Đang hoạt động**.
5. Bấm **gọi video / gọi thoại** → bên kia thấy cửa sổ gọi đến → **Trả lời**. Trong cuộc gọi: tắt/bật mic-cam, chia sẻ màn hình, ghi cuộc gọi, **Kết thúc**.
6. **Gọi nhóm:** tích chọn nhiều bạn đang online → thanh dưới hiện **Gọi video/thoại nhóm** (cần ≥3 tài khoản đã là bạn của người tạo nhóm).
7. Gọi xong, mục **Lịch sử cuộc gọi** tự cập nhật (lịch sử ghi cho cuộc gọi 1-1).

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
│   └── mongo-init.js                       # tạo collection + index: users, call_history, friendships
└── src/main/
    ├── java/com/project/vts/
    │   ├── VtsApplication.java
    │   ├── config/                          # SecurityConfig, WebSocketConfig
    │   ├── controller/                      # AuthController, UserController, FriendController, CallHistoryController
    │   ├── dto/                             # request + response (đều là record)
    │   ├── exception/                       # BadRequest/NotFound/Forbidden/Conflict + GlobalExceptionHandler
    │   ├── model/                           # User, CallHistory, Friendship, FriendshipStatus
    │   ├── repository/                      # UserRepository, CallHistoryRepository, FriendshipRepository
    │   ├── security/                        # JwtService, JwtAuthenticationFilter, CustomUserDetailsService
    │   ├── service/                         # AuthService, UserService, CallHistoryService, FriendshipService
    │   └── signaling/
    │       ├── SessionRegistry.java         # userId -> WebSocketSession (NGUỒN CHÂN LÝ presence)
    │       ├── SignalingHandler.java        # relay offer/answer/ICE + presence + lịch sử + "phòng" gọi nhóm
    │       └── JwtHandshakeInterceptor.java # xác thực token ở handshake
    └── resources/
        ├── application.properties
        └── static/
            ├── login.html
            ├── index.html                   # bạn bè / lời mời / tìm kiếm + overlay gọi 1-1 & nhóm + lịch sử
            └── js/
                ├── auth.js                  # quản lý JWT, apiFetch
                ├── signaling.js             # client WebSocket
                ├── webrtc.js                # engine gọi 1-1 + chia sẻ màn hình + ghi cuộc gọi
                └── group.js                 # engine gọi nhóm full-mesh (tối đa 4)
```

> Còn có `src/test/java/.../service/FriendshipServiceTest.java` — unit test máy trạng thái + phân quyền kết bạn.

## API

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Đăng ký (trả JWT) | Không |
| POST | `/api/auth/login` | Đăng nhập (trả JWT) | Không |
| GET | `/api/users/me` | Thông tin của mình | Bearer |
| GET | `/api/users/search?username=` | Tìm 1 người theo username chính xác (kèm quan hệ) | Bearer |
| GET | `/api/friends` | Danh sách bạn bè (ACCEPTED) | Bearer |
| GET | `/api/friends/requests/incoming` | Lời mời đến đang chờ | Bearer |
| GET | `/api/friends/requests/outgoing` | Lời mời đã gửi đang chờ | Bearer |
| POST | `/api/friends/requests` | Gửi lời mời `{username}` (auto-accept nếu chiều ngược đã tồn tại) | Bearer |
| POST | `/api/friends/requests/{id}/accept` | Chấp nhận (chỉ người nhận) | Bearer |
| POST | `/api/friends/requests/{id}/decline` | Từ chối (chỉ người nhận) | Bearer |
| DELETE | `/api/friends/requests/{id}` | Huỷ lời mời đã gửi (chỉ người gửi) | Bearer |
| DELETE | `/api/friends/{userId}` | Huỷ kết bạn | Bearer |
| GET | `/api/calls` | Lịch sử cuộc gọi của mình | Bearer |
| WS | `/ws?token=<JWT>` | Kênh signaling (presence bạn bè + offer/answer/ICE; gate quyền gọi) | Token qua query |

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

## Tính năng nâng cao

Đã hoàn thành:

- ✅ **Chia sẻ màn hình** — `getDisplayMedia` + `sender.replaceTrack` (không cần renegotiate).
- ✅ **Ghi cuộc gọi** — `MediaRecorder` phía client: video ghép qua `<canvas>` (đối phương full + camera mình PiP) + trộn âm thanh 2 chiều bằng Web Audio; thoại ghi audio. Xuất file `.webm`, tự tải về khi dừng/cúp máy.
- ✅ **Gọi nhóm (tối đa 4 người)** — full-mesh P2P (mỗi cặp một `RTCPeerConnection`), "phòng" quản lý ở signaling (RAM). Quy ước "người cũ offer người mới" để tránh glare; chỉ mời được bạn bè, các thành viên trong phòng relay được với nhau. Engine ở `static/js/group.js`.

Chưa làm (tùy chọn):

- **Gọi nhóm > 4 người** — cần **SFU** (vd mediasoup/Janus); full-mesh sẽ quá tải băng thông/CPU.
- **Ghi hình / chia sẻ màn hình trong cuộc gọi nhóm**, và **lịch sử cuộc gọi nhóm** (hiện lịch sử chỉ ghi cuộc gọi 1-1).