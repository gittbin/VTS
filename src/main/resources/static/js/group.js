// src/main/resources/static/js/group.js
// Gọi nhóm full-mesh (tối đa 4 người). Mỗi cặp một RTCPeerConnection.
// Quy ước tránh glare: "người đã ở trong phòng tạo offer tới người mới vào".
// Bản tin media nhóm mang thêm trường `room` để phân biệt với cuộc gọi 1-1 (webrtc.js bỏ qua nếu có `room`).

const GroupCall = (() => {
    const ICE_SERVERS = [
        { urls: 'stun:stun.l.google.com:19302' },
        // { urls: 'turn:YOUR_TURN_HOST:3478', username: 'user', credential: 'pass' }
    ];

    let localStream = null;
    let roomId = null, callType = 'video', state = 'idle';   // idle | active
    let pendingInvite = null;
    const peers = new Map();      // peerId -> { pc, name, pending: [], video, tile }
    let ui = null;

    const initials = (n) => (n || '?').trim().split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
    const myId = () => { const u = Auth.getUser(); return u && u.id; };
    const myName = () => { const u = Auth.getUser(); return (u && (u.displayName || u.username)) || 'Tôi'; };

    function icon(kind) {
        const I = {
            mic: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2"/><line x1="12" x2="12" y1="19" y2="22"/></svg>',
            'mic-off': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="2" x2="22" y1="2" y2="22"/><path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V5a3 3 0 0 0-5.94-.6"/><path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23"/><line x1="12" x2="12" y1="19" y2="22"/></svg>',
            cam: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m22 8-6 4 6 4V8Z"/><rect x="2" y="6" width="14" height="12" rx="2"/></svg>',
            'cam-off': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.66 6H14a2 2 0 0 1 2 2v2.34l1 1L22 8v8"/><path d="M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2l10 10Z"/><line x1="2" x2="22" y1="2" y2="22"/></svg>',
            end: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"/></svg>'
        };
        return I[kind] || '';
    }

    function buildUI() {
        if (ui) return ui;
        const overlay = document.createElement('div');
        overlay.className = 'group-overlay';
        overlay.innerHTML = `
          <div class="group-topbar">
            <span class="gtitle">Cuộc gọi nhóm</span>
            <span class="gcount"></span>
          </div>
          <div class="group-grid"></div>
          <div class="group-controls">
            <button class="ctrl-btn gmic" title="Tắt/bật micro"></button>
            <button class="ctrl-btn gcam" title="Tắt/bật camera"></button>
            <button class="ctrl-btn end gleave" title="Rời nhóm"></button>
          </div>`;
        document.body.appendChild(overlay);

        const incoming = document.createElement('div');
        incoming.className = 'incoming-modal';
        incoming.innerHTML = `
          <div class="incoming-card">
            <div class="incoming-avatar gincavatar"></div>
            <div class="incoming-name gincname"></div>
            <div class="incoming-sub gincsub"></div>
            <div class="incoming-actions">
              <button class="inc-btn reject gincreject">Từ chối</button>
              <button class="inc-btn accept gincaccept">Tham gia</button>
            </div>
          </div>`;
        document.body.appendChild(incoming);

        ui = {
            overlay, incoming,
            grid: overlay.querySelector('.group-grid'),
            count: overlay.querySelector('.gcount'),
            micBtn: overlay.querySelector('.gmic'),
            camBtn: overlay.querySelector('.gcam'),
            leaveBtn: overlay.querySelector('.gleave'),
            incAvatar: incoming.querySelector('.gincavatar'),
            incName: incoming.querySelector('.gincname'),
            incSub: incoming.querySelector('.gincsub'),
            acceptBtn: incoming.querySelector('.gincaccept'),
            rejectBtn: incoming.querySelector('.gincreject'),
        };
        ui.leaveBtn.innerHTML = icon('end');
        ui.micBtn.onclick = toggleMic;
        ui.camBtn.onclick = toggleCam;
        ui.leaveBtn.onclick = leave;
        ui.acceptBtn.onclick = acceptInvite;
        ui.rejectBtn.onclick = rejectInvite;
        return ui;
    }

    function gum(constraints, ms) {
        let done = false;
        const media = navigator.mediaDevices.getUserMedia(constraints).then(s => {
            if (done) { s.getTracks().forEach(t => t.stop()); throw new Error('late'); }
            done = true; return s;
        });
        const timeout = new Promise((_, rej) => setTimeout(() => {
            if (!done) { done = true; rej(new Error('Thiết bị bận / hết thời gian chờ')); }
        }, ms));
        return Promise.race([media, timeout]);
    }

    async function getLocalMedia(type) {
        const tries = type === 'audio'
            ? [{ video: false, audio: true }]
            : [{ video: true, audio: true }, { video: false, audio: true }];
        let stream = null;
        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
            for (const c of tries) {
                try { stream = await gum(c, 8000); break; }
                catch (e) { console.warn('[Group] getUserMedia lỗi:', e.name || e.message); }
            }
        }
        if (!stream) stream = new MediaStream();
        localStream = stream;
        const selfVideo = ui.grid.querySelector('.gtile.self video');
        if (selfVideo) { selfVideo.srcObject = localStream; selfVideo.play().catch(() => {}); }
        applyMicCam();
        return localStream;
    }

    // ---------- Ô video ----------
    function makeTile(id, name, isLocal) {
        const tile = document.createElement('div');
        tile.className = 'gtile' + (isLocal ? ' self' : '');
        tile.dataset.peer = id;
        const video = document.createElement('video');
        video.autoplay = true; video.playsInline = true;
        if (isLocal) { video.muted = true; video.style.transform = 'scaleX(-1)'; }
        const label = document.createElement('div');
        label.className = 'gname';
        label.textContent = name + (isLocal ? ' (Bạn)' : '');
        const ph = document.createElement('div');
        ph.className = 'gph';
        ph.textContent = initials(name);
        tile.append(video, ph, label);
        return { tile, video };
    }

    function addSelfTile() {
        if (ui.grid.querySelector('.gtile.self')) return;
        const { tile } = makeTile(myId() || 'me', myName(), true);
        ui.grid.appendChild(tile);
        relayout();
    }
    function addPeerTile(id, name) {
        const { tile, video } = makeTile(id, name, false);
        ui.grid.appendChild(tile);
        relayout();
        return video;
    }
    function removeTile(id) {
        const t = ui.grid.querySelector(`.gtile[data-peer="${CSS.escape(id)}"]`);
        if (t) t.remove();
        relayout();
    }
    function relayout() {
        const n = ui.grid.querySelectorAll('.gtile').length;
        ui.grid.style.gridTemplateColumns = n <= 1 ? '1fr' : 'repeat(2, 1fr)';
        ui.count.textContent = n + ' người';
    }

    // ---------- Kết nối mesh ----------
    function createPeer(peerId, name, makeOffer) {
        if (peers.has(peerId)) return peers.get(peerId);
        const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
        const video = addPeerTile(peerId, name);
        const ent = { pc, name, pending: [], video };
        peers.set(peerId, ent);

        if (localStream) localStream.getTracks().forEach(t => pc.addTrack(t, localStream));
        pc.onicecandidate = (e) => {
            if (e.candidate) Signaling.sendTo(peerId, { type: 'ice-candidate', room: roomId, candidate: e.candidate });
        };
        pc.ontrack = (e) => {
            const x = peers.get(peerId);
            if (x && x.video && x.video.srcObject !== e.streams[0]) {
                x.video.srcObject = e.streams[0];
                x.video.play().catch(() => {});
            }
        };
        pc.onconnectionstatechange = () => {
            if (pc.connectionState === 'failed') closePeer(peerId);
        };
        if (makeOffer) negotiate(peerId);
        return ent;
    }

    async function negotiate(peerId) {
        const ent = peers.get(peerId); if (!ent) return;
        try {
            const offer = await ent.pc.createOffer();
            await ent.pc.setLocalDescription(offer);
            Signaling.sendTo(peerId, { type: 'offer', room: roomId, sdp: ent.pc.localDescription });
        } catch (e) { console.warn('[Group] tạo offer lỗi:', e); }
    }

    async function drain(ent) {
        for (const c of ent.pending) { try { await ent.pc.addIceCandidate(c); } catch (e) {} }
        ent.pending = [];
    }

    function closePeer(peerId) {
        const ent = peers.get(peerId); if (!ent) return;
        try { ent.pc.close(); } catch (e) {}
        peers.delete(peerId);
        removeTile(peerId);
    }

    // ---------- Host ----------
    async function start(targets, type) {
        if (state !== 'idle') return;
        if (window.Call && Call.isBusy && Call.isBusy()) { alert('Bạn đang trong một cuộc gọi.'); return; }
        if (!targets || !targets.length) return;
        callType = type === 'audio' ? 'audio' : 'video';
        roomId = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));
        buildUI();
        state = 'active';
        openOverlay();
        addSelfTile();
        await getLocalMedia(callType);
        const fromName = myName();
        targets.forEach(t => Signaling.send({ type: 'group-invite', roomId, to: t.id, fromName, callType }));
        // Host chờ 'group-peer-joined' khi từng người chấp nhận → tạo offer tới họ.
    }

    // ---------- Người được mời ----------
    function onInvite(msg) {
        if (state !== 'idle') return;                                  // đang trong nhóm khác → bỏ qua
        if (window.Call && Call.isBusy && Call.isBusy()) return;       // đang gọi 1-1 → bỏ qua
        buildUI();
        pendingInvite = { roomId: msg.roomId, fromName: msg.fromName || 'Người dùng', callType: msg.callType === 'audio' ? 'audio' : 'video' };
        ui.incName.textContent = pendingInvite.fromName;
        ui.incAvatar.textContent = initials(pendingInvite.fromName);
        ui.incSub.textContent = (pendingInvite.callType === 'audio' ? 'mời bạn gọi thoại nhóm…' : 'mời bạn gọi video nhóm…');
        ui.incoming.classList.add('show');
    }

    async function acceptInvite() {
        const inv = pendingInvite; if (!inv) return;
        pendingInvite = null;
        ui.incoming.classList.remove('show');
        roomId = inv.roomId; callType = inv.callType;
        state = 'active';
        openOverlay();
        addSelfTile();
        await getLocalMedia(callType);
        Signaling.send({ type: 'group-join', roomId });               // server sẽ trả 'group-joined' + báo người cũ offer mình
    }

    function rejectInvite() {
        pendingInvite = null;
        if (ui) ui.incoming.classList.remove('show');
    }

    function onJoined(msg) {            // mình là người mới → tạo pc với từng người cũ và CHỜ offer
        if (msg.roomId !== roomId || state !== 'active') return;
        (msg.peers || []).forEach(p => createPeer(p.id, p.name, false));
    }
    function onPeerJoined(msg) {        // mình là người cũ → có người mới vào, mình tạo offer
        if (msg.roomId !== roomId || state !== 'active') return;
        createPeer(msg.peerId, msg.peerName || 'Người dùng', true);
    }

    async function onOffer(msg) {
        if (!msg.room || msg.room !== roomId || state !== 'active') return;
        let ent = peers.get(msg.from) || createPeer(msg.from, 'Người dùng', false);
        try {
            await ent.pc.setRemoteDescription(msg.sdp);
            await drain(ent);
            const answer = await ent.pc.createAnswer();
            await ent.pc.setLocalDescription(answer);
            Signaling.sendTo(msg.from, { type: 'answer', room: roomId, sdp: ent.pc.localDescription });
        } catch (e) { console.warn('[Group] xử lý offer lỗi:', e); }
    }
    async function onAnswer(msg) {
        if (!msg.room || msg.room !== roomId) return;
        const ent = peers.get(msg.from); if (!ent) return;
        try { await ent.pc.setRemoteDescription(msg.sdp); await drain(ent); } catch (e) {}
    }
    async function onIce(msg) {
        if (!msg.room || msg.room !== roomId || !msg.candidate) return;
        const ent = peers.get(msg.from); if (!ent) return;
        if (ent.pc.remoteDescription && ent.pc.remoteDescription.type) {
            try { await ent.pc.addIceCandidate(msg.candidate); } catch (e) {}
        } else {
            ent.pending.push(msg.candidate);
        }
    }
    function onPeerLeft(msg) {
        if (msg.roomId !== roomId) return;
        closePeer(msg.peerId);
    }
    function onGroupFull() { alert('Nhóm đã đầy (tối đa 4 người).'); if (state === 'active' && peers.size === 0) cleanup(); }
    function onGroupClosed(msg) { if (msg.roomId === roomId) { alert('Phòng không còn tồn tại.'); cleanup(); } }

    // ---------- Điều khiển ----------
    function applyMicCam() {
        const a = localStream && localStream.getAudioTracks()[0];
        const v = localStream && localStream.getVideoTracks()[0];
        if (a) a.enabled = micOn;
        if (v) v.enabled = camOn;
        if (ui) {
            ui.micBtn.classList.toggle('off', !micOn); ui.micBtn.innerHTML = icon(micOn ? 'mic' : 'mic-off');
            ui.camBtn.innerHTML = icon(camOn ? 'cam' : 'cam-off'); ui.camBtn.classList.toggle('off', !camOn);
            ui.camBtn.style.display = (callType === 'video') ? '' : 'none';
            const selfTile = ui.grid.querySelector('.gtile.self');
            if (selfTile) selfTile.classList.toggle('camoff', !camOn || callType !== 'video');
        }
    }
    let micOn = true, camOn = true;
    function toggleMic() { micOn = !micOn; applyMicCam(); }
    function toggleCam() { camOn = !camOn; applyMicCam(); }

    function openOverlay() { applyMicCam(); ui.overlay.classList.add('show'); }
    function closeOverlay() { if (ui) { ui.overlay.classList.remove('show'); ui.incoming.classList.remove('show'); } }

    function leave() {
        if (state === 'idle') return;
        if (roomId) Signaling.send({ type: 'group-leave', roomId });
        cleanup();
    }
    function cleanup() {
        peers.forEach(ent => { try { ent.pc.close(); } catch (e) {} });
        peers.clear();
        if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
        if (ui) ui.grid.innerHTML = '';
        closeOverlay();
        state = 'idle'; roomId = null; pendingInvite = null; micOn = true; camOn = true;
    }

    function isBusy() { return state !== 'idle'; }

    function init() {
        buildUI();
        Signaling.on('group-invite', onInvite);
        Signaling.on('group-joined', onJoined);
        Signaling.on('group-peer-joined', onPeerJoined);
        Signaling.on('group-peer-left', onPeerLeft);
        Signaling.on('group-full', onGroupFull);
        Signaling.on('group-closed', onGroupClosed);
        Signaling.on('offer', onOffer);
        Signaling.on('answer', onAnswer);
        Signaling.on('ice-candidate', onIce);
    }

    return { init, start, isBusy };
})();
