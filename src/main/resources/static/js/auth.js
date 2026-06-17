// src/main/resources/static/js/auth.js
// Quản lý phiên đăng nhập phía client: lưu JWT, gắn header, bảo vệ trang.

const TOKEN_KEY = 'vts_token';
const USER_KEY = 'vts_user';

const Auth = {
    saveSession(authResponse) {
        localStorage.setItem(TOKEN_KEY, authResponse.token);
        localStorage.setItem(USER_KEY, JSON.stringify(authResponse.user));
    },

    getToken() {
        return localStorage.getItem(TOKEN_KEY);
    },

    getUser() {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    },

    isLoggedIn() {
        return !!this.getToken();
    },

    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    },

    logout() {
        this.clear();
        window.location.href = '/login.html';
    },

    // Gọi ở đầu các trang cần đăng nhập; chưa đăng nhập thì đá về trang login
    requireAuth() {
        if (!this.isLoggedIn()) window.location.href = '/login.html';
    },

    // fetch tự gắn token; token hết hạn (401) thì tự đăng xuất
    async apiFetch(url, options = {}) {
        const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
        const token = this.getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        const res = await fetch(url, { ...options, headers });
        if (res.status === 401) {
            this.logout();
            throw new Error('Phiên đăng nhập đã hết hạn');
        }
        return res;
    }
};