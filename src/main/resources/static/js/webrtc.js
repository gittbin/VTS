// src/main/resources/static/js/webrtc.js
// Engine gọi 1-1 (WebRTC). Hỗ trợ 'audio' (chỉ mic) và 'video' (mic + camera) + chia sẻ màn hình + ghi cuộc gọi.
// Trao đổi offer/answer/ICE + điều khiển cuộc gọi qua Signaling. Chạy trong overlay trên index.html.

const Call = (() => {
    const ICE_SERVERS = [
        { urls: 'stun:stun.l.google.com:19302' },
        // { urls: 'turn:YOUR_TURN_HOST:3478', username: 'user', credential: 'pass' }
    ];
    const RING_TIMEOUT_MS = 30000;

    let pc = null, localStream = null;
    let peerId = null, peerName = null, role = null;
    let callType = 'video';
    let state = 'idle';                  // idle | calling | incoming | connecting | in-call
    let pendingCandidates = [];
    let ringTimer = null, durationTimer = null, startedAt = 0;
    let callId = null;
    let onFinished = null;
    let screenStream = null;             // luồng màn hình khi đang chia sẻ
    let ui = null;

    // Ghi cuộc gọi (MediaRecorder)
    let recorder = null, recChunks = [], recAudioCtx = null, recCanvas = null, recRafId = 0;
    let recording = false, recPeerName = '', recStamp = '';

    const initials = (n) => (n || '?').trim().split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();

    function icon(kind) {
        const I = {
            mic: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2"/><line x1="12" x2="12" y1="19" y2="22"/></svg>',
            'mic-off': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="2" x2="22" y1="2" y2="22"/><path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V5a3 3 0 0 0-5.94-.6"/><path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23"/><line x1="12" x2="12" y1="19" y2="22"/></svg>',
            cam: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m22 8-6 4 6 4V8Z"/><rect x="2" y="6" width="14" height="12" rx="2"/></svg>',
            'cam-off': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.66 6H14a2 2 0 0 1 2 2v2.34l1 1L22 8v8"/><path d="M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2l10 10Z"/><line x1="2" x2="22" y1="2" y2="22"/></svg>',
            screen: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3"/><path d="M8 21h8"/><path d="M12 17v4"/><path d="m17 8 5-5"/><path d="M17 3h5v5"/></svg>',
            rec: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4" fill="currentColor" stroke="none"/></svg>',
            'rec-stop': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><rect x="9" y="9" width="6" height="6" rx="1.5" fill="currentColor" stroke="none"/></svg>',
            end: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"/></svg>'
        };
        return I[kind] || '';
    }

    function buildUI() {
        if (ui) return ui;
        const overlay = document.createElement('div');
        overlay.className = 'call-overlay';
        overlay.innerHTML = `
          <video class="remote-video" autoplay playsinline></video>
          <div class="call-stage-empty">
            <div class="big-avatar"></div>
            <div class="call-peer-name"></div>
            <div class="call-status"></div>
          </div>
          <video class="local-video" autoplay playsinline muted></video>
          <div class="call-topbar">
            <span class="call-peer-name-top"></span>
            <span class="call-timer"></span>
            <span class="rec-dot">REC</span>
          </div>
          <div class="call-controls">
            <button class="ctrl-btn mic" title="Tắt/bật micro"></button>
            <button class="ctrl-btn cam" title="Tắt/bật camera"></button>
            <button class="ctrl-btn screen" title="Chia sẻ màn hình"></button>
            <button class="ctrl-btn rec" title="Ghi cuộc gọi"></button>
            <button class="ctrl-btn end" title="Kết thúc"></button>
          </div>`;
        document.body.appendChild(overlay);

        const incoming = document.createElement('div');
        incoming.className = 'incoming-modal';
        incoming.innerHTML = `
          <div class="incoming-card">
            <div class="incoming-avatar"></div>
            <div class="incoming-name"></div>
            <div class="incoming-sub"></div>
            <div class="incoming-actions">
              <button class="inc-btn reject">Từ chối</button>
              <button class="inc-btn accept">Trả lời</button>
            </div>
          </div>`;
        document.body.appendChild(incoming);

        ui = {
            overlay, incoming,
            remoteVideo: overlay.querySelector('.remote-video'),
            localVideo: overlay.querySelector('.local-video'),
            stageEmpty: overlay.querySelector('.call-stage-empty'),
            bigAvatar: overlay.querySelector('.big-avatar'),
            stagePeerName: overlay.querySelector('.call-peer-name'),
            status: overlay.querySelector('.call-status'),
            topName: overlay.querySelector('.call-peer-name-top'),
            timer: overlay.querySelector('.call-timer'),
            micBtn: overlay.querySelector('.ctrl-btn.mic'),
            camBtn: overlay.querySelector('.ctrl-btn.cam'),
            screenBtn: overlay.querySelector('.ctrl-btn.screen'),
            recBtn: overlay.querySelector('.ctrl-btn.rec'),
            recDot: overlay.querySelector('.rec-dot'),
            endBtn: overlay.querySelector('.ctrl-btn.end'),
            incAvatar: incoming.querySelector('.incoming-avatar'),
            incName: incoming.querySelector('.incoming-name'),
            incSub: incoming.querySelector('.incoming-sub'),
            acceptBtn: incoming.querySelector('.inc-btn.accept'),
            rejectBtn: incoming.querySelector('.inc-btn.reject'),
        };

        resetControlIcons();
        ui.endBtn.innerHTML = icon('end');
        ui.endBtn.onclick = hangup;
        ui.micBtn.onclick = toggleMic;
        ui.camBtn.onclick = toggleCam;
        ui.screenBtn.onclick = toggleScreenShare;
        ui.recBtn.onclick = toggleRecord;
        ui.acceptBtn.onclick = acceptIncoming;
        ui.rejectBtn.onclick = rejectIncoming;
        return ui;
    }

    function gumWithTimeout(constraints, ms) {
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
                try { stream = await gumWithTimeout(c, 8000); break; }
                catch (e) { console.warn('[Call] getUserMedia lỗi:', e.name || e.message, '| yêu cầu:', JSON.stringify(c)); }
            }
        } else {
            console.warn('[Call] navigator.mediaDevices không khả dụng — trang không chạy ở localhost/HTTPS?');
        }
        if (!stream) stream = new MediaStream();
        localStream = stream;
        ui.localVideo.srcObject = localStream;
        ui.localVideo.style.display = localStream.getVideoTracks().length ? '' : 'none';
        ui.localVideo.play().catch(() => {});
        return localStream;
    }

    function createPeerConnection() {
        pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
        pc.onicecandidate = (e) => {
            if (e.candidate) Signaling.sendTo(peerId, { type: 'ice-candidate', candidate: e.candidate });
        };
        pc.ontrack = (e) => {
            if (ui.remoteVideo.srcObject !== e.streams[0]) {
                ui.remoteVideo.srcObject = e.streams[0];
                ui.remoteVideo.play().catch(() => {});
            }
            onConnected();
        };
        pc.onconnectionstatechange = () => {
            const s = pc.connectionState;
            if (s === 'connected') onConnected();
            else if (s === 'failed') cleanup('Mất kết nối');
            else if (s === 'disconnected' && state === 'in-call') setStatus('Đang kết nối lại…');
        };
    }

    function addLocalTracks() {
        const audio = localStream.getAudioTracks()[0];
        const video = localStream.getVideoTracks()[0];
        if (audio) pc.addTrack(audio, localStream); else pc.addTransceiver('audio', { direction: 'recvonly' });
        if (callType === 'video') {
            if (video) pc.addTrack(video, localStream); else pc.addTransceiver('video', { direction: 'recvonly' });
        }
    }

    async function drainCandidates() {
        for (const c of pendingCandidates) { try { await pc.addIceCandidate(c); } catch (e) {} }
        pendingCandidates = [];
    }

    // ---------- Caller ----------
    async function start(targetId, targetName, type) {
        if (state !== 'idle') return;
        buildUI();
        role = 'caller'; peerId = targetId; peerName = targetName || 'Người dùng'; callType = type || 'video';
        callId = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));
        state = 'calling';
        openStage('Đang gọi…');
        await getLocalMedia(callType);
        if (state !== 'calling') return;
        const u = Auth.getUser();
        Signaling.sendTo(peerId, { type: 'call-request', callId, callType, fromName: (u?.displayName || u?.username) });
        ringTimer = setTimeout(() => {
            if (state === 'calling') { Signaling.sendTo(peerId, { type: 'call-cancel', callId }); cleanup('Không trả lời'); }
        }, RING_TIMEOUT_MS);
    }

    async function onCallAccept(msg) {
        if (role !== 'caller' || msg.from !== peerId) return;
        clearTimeout(ringTimer);
        createPeerConnection();
        addLocalTracks();
        state = 'connecting'; setStatus('Đang kết nối…');
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        Signaling.sendTo(peerId, { type: 'offer', sdp: pc.localDescription });
    }

    async function onAnswer(msg) {
        if (msg.from !== peerId || !pc) return;
        await pc.setRemoteDescription(msg.sdp);
        await drainCandidates();
    }

    // ---------- Callee ----------
    function onCallRequest(msg) {
        if (state !== 'idle') {
            Signaling.sendTo(msg.from, { type: 'call-reject', reason: 'busy', callId: msg.callId });
            return;
        }
        buildUI();
        role = 'callee'; peerId = msg.from; peerName = msg.fromName || 'Người dùng';
        callType = msg.callType === 'audio' ? 'audio' : 'video';
        callId = msg.callId; state = 'incoming';
        ui.incName.textContent = peerName;
        ui.incAvatar.textContent = initials(peerName);
        ui.incSub.textContent = callType === 'audio' ? 'cuộc gọi thoại đến…' : 'cuộc gọi video đến…';
        ui.incoming.classList.add('show');
    }

    async function acceptIncoming() {
        ui.incoming.classList.remove('show');
        openStage('Đang kết nối…');
        await getLocalMedia(callType);
        if (state !== 'incoming') return;
        createPeerConnection();
        addLocalTracks();
        state = 'connecting';
        Signaling.sendTo(peerId, { type: 'call-accept', callId });
    }

    async function onOffer(msg) {
        if (msg.from !== peerId || !pc) return;
        await pc.setRemoteDescription(msg.sdp);
        await drainCandidates();
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        Signaling.sendTo(peerId, { type: 'answer', sdp: pc.localDescription });
    }

    function rejectIncoming() {
        if (peerId) Signaling.sendTo(peerId, { type: 'call-reject', callId });
        cleanup();
    }

    function hangup() {
        if (peerId) Signaling.sendTo(peerId, { type: state === 'calling' ? 'call-cancel' : 'call-end', callId });
        cleanup();
    }

    // ---------- Dùng chung ----------
    async function onIce(msg) {
        if (msg.from !== peerId || !msg.candidate) return;
        if (pc && pc.remoteDescription && pc.remoteDescription.type) {
            try { await pc.addIceCandidate(msg.candidate); } catch (e) {}
        } else {
            pendingCandidates.push(msg.candidate);
        }
    }

    function onCallReject(msg) {
        if (msg.from !== peerId) return;
        cleanup(msg.reason === 'busy' ? 'Máy bận' : 'Cuộc gọi bị từ chối');
    }
    function onCallCancel(msg) { if (msg.from === peerId) cleanup(); }
    function onCallEnd(msg) { if (msg.from === peerId) cleanup('Cuộc gọi đã kết thúc'); }
    function onPeerUnavailable(msg) { if (state === 'calling') cleanup('Người dùng không trực tuyến'); }

    function toggleMic() {
        const t = localStream && localStream.getAudioTracks()[0]; if (!t) return;
        t.enabled = !t.enabled;
        ui.micBtn.classList.toggle('off', !t.enabled);
        ui.micBtn.innerHTML = icon(t.enabled ? 'mic' : 'mic-off');
    }
    function toggleCam() {
        const t = localStream && localStream.getVideoTracks()[0]; if (!t) return;
        t.enabled = !t.enabled;
        ui.camBtn.classList.toggle('off', !t.enabled);
        ui.camBtn.innerHTML = icon(t.enabled ? 'cam' : 'cam-off');
        if (!screenStream) ui.localVideo.style.opacity = t.enabled ? '1' : '0.35';
    }

    // ---------- Chia sẻ màn hình ----------
    async function toggleScreenShare() {
        if (screenStream) stopScreenShare();
        else await startScreenShare();
    }

    async function startScreenShare() {
        const sender = pc && pc.getSenders().find(s => s.track && s.track.kind === 'video');
        if (!sender) { alert('Chưa chia sẻ được — cuộc gọi video chưa kết nối, hoặc không có luồng camera để thay.'); return; }
        let stream;
        try {
            stream = await navigator.mediaDevices.getDisplayMedia({ video: true });
        } catch (e) {
            console.warn('[Call] getDisplayMedia lỗi/đã hủy:', e.name || e.message);
            return;   // người dùng bấm Hủy ở hộp chọn màn hình
        }
        screenStream = stream;
        const screenTrack = stream.getVideoTracks()[0];
        await sender.replaceTrack(screenTrack);          // peer thấy màn hình thay camera — KHÔNG cần renegotiate
        ui.localVideo.srcObject = stream;                // preview hiện màn hình
        ui.localVideo.style.transform = 'none';          // không lật gương nội dung màn hình
        ui.localVideo.style.opacity = '1';
        ui.screenBtn.classList.add('active');
        ui.screenBtn.title = 'Dừng chia sẻ';
        screenTrack.onended = () => stopScreenShare();   // bấm "Stop sharing" của trình duyệt
    }

    function stopScreenShare() {
        const sender = pc && pc.getSenders().find(s => s.track && s.track.kind === 'video');
        const camTrack = localStream && localStream.getVideoTracks()[0];
        if (sender) sender.replaceTrack(camTrack || null);   // trả lại camera (hoặc tắt video nếu không có cam)
        if (screenStream) { screenStream.getTracks().forEach(t => t.stop()); screenStream = null; }
        if (ui) {
            ui.localVideo.srcObject = localStream;
            ui.localVideo.style.transform = 'scaleX(-1)';     // lật gương lại cho camera
            ui.localVideo.style.display = (camTrack ? '' : 'none');
            ui.screenBtn.classList.remove('active');
            ui.screenBtn.title = 'Chia sẻ màn hình';
        }
    }

    // ---------- Ghi cuộc gọi (MediaRecorder) ----------
    function recSupported() {
        return typeof MediaRecorder !== 'undefined'
            && !!navigator.mediaDevices                                  // cần secure context (localhost/HTTPS)
            && typeof HTMLCanvasElement.prototype.captureStream === 'function';
    }

    function pickRecMime(isVideo) {
        const cands = isVideo
            ? ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
            : ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus'];
        return cands.find(t => { try { return MediaRecorder.isTypeSupported(t); } catch (e) { return false; } }) || '';
    }

    // Vẽ video lên canvas theo kiểu object-fit: cover (cắt giữa, lấp đầy khung)
    function drawCover(c, video, dx, dy, dw, dh) {
        const vw = video.videoWidth, vh = video.videoHeight;
        if (!vw || !vh) return;
        const scale = Math.max(dw / vw, dh / vh);
        const sw = dw / scale, sh = dh / scale;
        c.drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, dx, dy, dw, dh);
    }

    async function toggleRecord() {
        if (recording) stopRecording();
        else await startRecording();
    }

    async function startRecording() {
        if (recording) return;
        if (state !== 'in-call') { alert('Chỉ ghi được khi cuộc gọi đã kết nối.'); return; }
        if (!recSupported()) { alert('Trình duyệt không hỗ trợ ghi (cần Chrome/Edge/Firefox mới, chạy ở localhost hoặc HTTPS).'); return; }

        try {
            const isVideo = callType === 'video';
            const remoteStream = ui.remoteVideo.srcObject;

            // 1) Trộn âm thanh 2 chiều (mic của mình + tiếng đối phương) qua Web Audio
            recAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
            if (recAudioCtx.state === 'suspended') { try { await recAudioCtx.resume(); } catch (e) {} }
            const dest = recAudioCtx.createMediaStreamDestination();
            if (localStream && localStream.getAudioTracks().length)
                recAudioCtx.createMediaStreamSource(new MediaStream(localStream.getAudioTracks())).connect(dest);
            if (remoteStream && remoteStream.getAudioTracks().length)
                recAudioCtx.createMediaStreamSource(new MediaStream(remoteStream.getAudioTracks())).connect(dest);
            const audioTracks = dest.stream.getAudioTracks();

            // 2) Tạo luồng để ghi: video → ghép canvas (đối phương full + mình PiP); thoại → chỉ audio
            let mixed;
            if (isVideo) {
                recCanvas = document.createElement('canvas');
                recCanvas.width = 1280; recCanvas.height = 720;
                const c = recCanvas.getContext('2d');
                const drawFrame = () => {
                    c.fillStyle = '#0E0B09'; c.fillRect(0, 0, recCanvas.width, recCanvas.height);
                    if (ui.remoteVideo.videoWidth) drawCover(c, ui.remoteVideo, 0, 0, recCanvas.width, recCanvas.height);
                    const lv = ui.localVideo;
                    if (lv.videoWidth && lv.style.display !== 'none') {              // ảnh nhỏ góc phải (PiP)
                        const pw = Math.round(recCanvas.width * 0.24);
                        const ph = Math.round(pw * (lv.videoHeight / lv.videoWidth));
                        drawCover(c, lv, recCanvas.width - pw - 24, 24, pw, ph);
                    }
                    recRafId = requestAnimationFrame(drawFrame);
                };
                drawFrame();
                const canvasStream = recCanvas.captureStream(30);
                mixed = new MediaStream([...canvasStream.getVideoTracks(), ...audioTracks]);
            } else {
                mixed = new MediaStream(audioTracks);
            }

            if (!mixed.getTracks().length) { alert('Chưa có luồng âm thanh/hình để ghi.'); cleanupRecording(); return; }

            const mime = pickRecMime(isVideo);
            recorder = new MediaRecorder(mixed, mime ? { mimeType: mime } : undefined);
            recChunks = [];
            recPeerName = peerName || 'cuoc-goi';
            recStamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
            recorder.ondataavailable = (e) => { if (e.data && e.data.size) recChunks.push(e.data); };
            recorder.onstop = () => finalizeRecording((recorder && recorder.mimeType) || mime, isVideo);
            recorder.start(1000);                                                    // gom dữ liệu mỗi giây → hạn chế mất khi sự cố
            recording = true;
            updateRecUI();
        } catch (e) {
            console.warn('[Call] Bắt đầu ghi lỗi:', e);
            alert('Không bắt đầu ghi được: ' + (e.message || e.name));
            cleanupRecording();
        }
    }

    function stopRecording() {
        recording = false;
        updateRecUI();
        if (recorder && recorder.state !== 'inactive') {
            try { recorder.stop(); return; } catch (e) {}                            // onstop sẽ tạo blob & tải file
        }
        cleanupRecording();
    }

    function finalizeRecording(mime, isVideo) {
        if (recRafId) { cancelAnimationFrame(recRafId); recRafId = 0; }
        const chunks = recChunks; recChunks = [];
        if (chunks.length) {
            const type = mime || (isVideo ? 'video/webm' : 'audio/webm');
            const blob = new Blob(chunks, { type });
            const safe = (recPeerName.trim().replace(/[^\p{L}\p{N}]+/gu, '_').replace(/^_+|_+$/g, '')) || 'cuoc-goi';
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url; a.download = `vts-${safe}-${recStamp}.webm`;
            document.body.appendChild(a); a.click(); a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 15000);
        }
        cleanupRecording();
    }

    function cleanupRecording() {
        if (recRafId) { cancelAnimationFrame(recRafId); recRafId = 0; }
        if (recAudioCtx) { try { recAudioCtx.close(); } catch (e) {} recAudioCtx = null; }
        recCanvas = null; recorder = null; recChunks = [];
        recording = false;
        updateRecUI();
    }

    function updateRecUI() {
        if (!ui) return;
        ui.recBtn.classList.toggle('active', recording);
        ui.recBtn.innerHTML = icon(recording ? 'rec-stop' : 'rec');
        ui.recBtn.title = recording ? 'Dừng ghi' : 'Ghi cuộc gọi';
        ui.recDot.classList.toggle('on', recording);
    }

    function resetControlIcons() {
        ui.micBtn.classList.remove('off'); ui.micBtn.innerHTML = icon('mic');
        ui.camBtn.classList.remove('off'); ui.camBtn.innerHTML = icon('cam');
        ui.screenBtn.classList.remove('active'); ui.screenBtn.innerHTML = icon('screen'); ui.screenBtn.title = 'Chia sẻ màn hình';
        ui.recBtn.classList.remove('active'); ui.recBtn.innerHTML = icon('rec'); ui.recBtn.title = 'Ghi cuộc gọi';
        ui.recDot.classList.remove('on');
        ui.localVideo.style.opacity = '1';
        ui.localVideo.style.transform = 'scaleX(-1)';
    }

    function openStage(statusText) {
        ui.bigAvatar.textContent = initials(peerName);
        ui.stagePeerName.textContent = peerName;
        ui.topName.textContent = peerName;
        ui.timer.textContent = '';
        setStatus(statusText);
        ui.stageEmpty.style.display = '';
        ui.remoteVideo.style.opacity = '0';
        resetControlIcons();
        ui.camBtn.style.display = callType === 'video' ? '' : 'none';
        ui.screenBtn.style.display = callType === 'video' ? '' : 'none';   // chỉ video mới chia sẻ màn hình
        ui.recBtn.disabled = true;                                         // chỉ ghi được khi cuộc gọi đã kết nối
        ui.overlay.classList.add('show');
    }
    function setStatus(t) { ui.status.textContent = t; }

    function onConnected() {
        if (state === 'in-call') return;
        state = 'in-call';
        ui.recBtn.disabled = false;                  // đã kết nối → cho phép ghi
        if (callType === 'video') {
            ui.stageEmpty.style.display = 'none';
            ui.remoteVideo.style.opacity = '1';
        }
        startedAt = Date.now();
        clearInterval(durationTimer);
        durationTimer = setInterval(updateTimer, 1000);
        updateTimer();
    }
    function updateTimer() {
        const s = Math.floor((Date.now() - startedAt) / 1000);
        const t = String((s / 60) | 0).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
        if (callType === 'video') { ui.timer.textContent = t; }
        else { setStatus(t); ui.timer.textContent = ''; }
    }

    function closeOverlay() {
        if (!ui) return;
        ui.overlay.classList.remove('show');
        ui.incoming.classList.remove('show');
    }
    function resetState() { state = 'idle'; role = null; peerId = null; peerName = null; callId = null; }

    function cleanup(message) {
        if (recorder && recorder.state !== 'inactive') { try { recorder.stop(); } catch (e) {} recording = false; }   // đang ghi mà cúp máy → chốt & tải bản ghi
        clearTimeout(ringTimer); clearInterval(durationTimer); ringTimer = durationTimer = null;
        if (screenStream) { screenStream.getTracks().forEach(t => t.stop()); screenStream = null; }
        if (pc) { try { pc.close(); } catch (e) {} pc = null; }
        if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
        pendingCandidates = [];
        const hadUI = !!ui;
        if (hadUI) { ui.remoteVideo.srcObject = null; ui.localVideo.srcObject = null; }
        resetState();
        if (typeof onFinished === 'function') { try { onFinished(); } catch (e) {} }

        if (!hadUI) return;
        if (message) {
            ui.incoming.classList.remove('show');
            ui.stageEmpty.style.display = ''; ui.remoteVideo.style.opacity = '0'; ui.timer.textContent = '';
            setStatus(message);
            ui.overlay.classList.add('show');
            setTimeout(() => { if (state === 'idle') closeOverlay(); }, 1600);
        } else {
            closeOverlay();
        }
    }

    function init() {
        buildUI();
        Signaling.on('call-request', onCallRequest);
        Signaling.on('call-accept', onCallAccept);
        Signaling.on('call-reject', onCallReject);
        Signaling.on('call-cancel', onCallCancel);
        Signaling.on('call-end', onCallEnd);
        Signaling.on('offer', onOffer);
        Signaling.on('answer', onAnswer);
        Signaling.on('ice-candidate', onIce);
        Signaling.on('peer-unavailable', onPeerUnavailable);
    }

    return { init, start, setOnFinished: (fn) => { onFinished = fn; } };
})();