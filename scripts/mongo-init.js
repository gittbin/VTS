// scripts/mongo-init.js
// Tạo collection + index cho VTS. Chạy: mongosh "<connection-string>" scripts/mongo-init.js
// (App đã bật auto-index-creation nên index từ @Indexed cũng tự tạo; script này để bàn giao + khởi tạo thủ công.)

const dbConn = db.getSiblingDB('vts');   // ĐỔI 'vts' cho khớp tên DB trong connection string

// users
dbConn.createCollection('users');
dbConn.users.createIndex({ username: 1 }, { unique: true });
dbConn.users.createIndex({ email: 1 }, { unique: true, sparse: true });

// call_history
dbConn.createCollection('call_history');
dbConn.call_history.createIndex({ callerId: 1 });
dbConn.call_history.createIndex({ calleeId: 1 });
dbConn.call_history.createIndex({ createdAt: -1 });

// friendships — quan hệ bạn bè (mô hình friends-only)
dbConn.createCollection('friendships');
dbConn.friendships.createIndex({ pairKey: 1 }, { unique: true });   // mỗi cặp chỉ 1 bản ghi (chống trùng + race)
dbConn.friendships.createIndex({ requesterId: 1 });
dbConn.friendships.createIndex({ addresseeId: 1 });
dbConn.friendships.createIndex({ requesterId: 1, status: 1 });
dbConn.friendships.createIndex({ addresseeId: 1, status: 1 });

// (tùy chọn) refresh_tokens — cho refresh token sau này; TTL tự xóa khi hết hạn
// dbConn.createCollection('refresh_tokens');
// dbConn.refresh_tokens.createIndex({ userId: 1 });
// dbConn.refresh_tokens.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

print('VTS: tạo collection + index xong.');