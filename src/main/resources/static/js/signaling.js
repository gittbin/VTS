// src/main/resources/static/js/signaling.js
// Kết nối WebSocket /ws: xử lý presence + relay bản tin (offer/answer/ICE dùng ở bước WebRTC).

const Signaling = {
    socket: null,
    handlers: {},          // type -> [fn]
    _retries: 0,
    _maxRetries: 6,
    _pingTimer: null,
    _shouldRun: false,

    on(type, fn) {
        (this.handlers[type] ||= []).push(fn);
        return this;
    },

    _emit(msg) {
        (this.handlers[msg.type] || []).forEach(fn => {
            try { fn(msg); } catch (e) { console.error('signaling handler error', e); }
        });
    },

    connect() {
        if (!Auth.isLoggedIn()) return;
        this._shouldRun = true;
        const token = Auth.getToken();
        const proto = location.protocol === 'https:' ? 'wss' : 'ws';
        const ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`);
        this.socket = ws;

        ws.onopen = () => {
            this._retries = 0;
            this._emit({ type: '_open' });
            clearInterval(this._pingTimer);
            this._pingTimer = setInterval(() => this.send({ type: 'ping' }), 25000);   // keepalive
        };

        ws.onmessage = (e) => {
            let msg;
            try { msg = JSON.parse(e.data); } catch { return; }
            this._emit(msg);
        };

        ws.onclose = () => {
            clearInterval(this._pingTimer);
            this._emit({ type: '_close' });
            this.socket = null;
            // Tự kết nối lại (server restart, rớt mạng 4G…), trừ khi đã đăng xuất.
            if (this._shouldRun && Auth.isLoggedIn() && this._retries < this._maxRetries) {
                const delay = Math.min(1000 * 2 ** this._retries, 15000);
                this._retries++;
                setTimeout(() => this.connect(), delay);
            }
        };

        ws.onerror = () => { try { ws.close(); } catch {} };
    },

    disconnect() {
        this._shouldRun = false;
        clearInterval(this._pingTimer);
        if (this.socket) { try { this.socket.close(); } catch {} this.socket = null; }
    },

    send(obj) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify(obj));
            return true;
        }
        return false;
    },

    /** Gửi bản tin point-to-point tới 1 user (server tự thêm "from"). Dùng cho WebRTC. */
    sendTo(toUserId, payload) {
        return this.send({ ...payload, to: toUserId });
    }
};