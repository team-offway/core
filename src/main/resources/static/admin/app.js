'use strict';

/**
 * OffWay 백오피스 (#343).
 *
 * 빌드 도구가 없다. 브라우저가 이 파일을 그대로 읽는다 — 배포 대상을 늘리지 않으려는 선택이라,
 * 여기에 번들러가 필요한 문법(import·JSX)을 들이지 않는다.
 */

// ---------------------------------------------------------------- 상수

/** 백오피스 API. 전부 ROLE_ADMIN 뒤에 있다. */
const LINKS_API = '/api/v1/admin/curated-links';

const UPLOADS_API = '/api/v1/admin/uploads';

/** ThumbnailUpload.MAX_BYTES. 서버가 거절하기 전에 화면이 먼저 알려준다. */
const MAX_THUMB_BYTES = 5 * 1024 * 1024;

/**
 * 토큰을 sessionStorage 에 둔다 — 탭을 닫으면 사라진다.
 *
 * localStorage 로 두면 공용 PC 에서 다음 사람에게 그대로 남는다. 백오피스는 하루 몇 번 여는 화면이라
 * 탭마다 다시 누르는 비용이 그 위험보다 싸다.
 */
const TOKEN_KEY = 'offway.admin.accessToken';

const ADMIN_ROLE = 'ADMIN';

/** 서버의 Paging.MAX_SIZE. 이보다 크게 요청해도 서버가 잘라서, 페이지를 돌지 않으면 뒤가 잘린다. */
const PAGE_SIZE = 100;

/** CuratedLink.MAX_CHIP_TEXT_LENGTH. 서버가 거절하기 전에 화면이 먼저 알려준다. */
const MAX_CHIP_TEXT = 30;

const SURFACE_LABELS = {
    HOME: '홈',
    REGION: '지역',
    COURSE: '코스',
    POI: '장소',
};

/** 로그인 콜백이 프래그먼트로 전하는 실패 사유 — WebLoginFailure 와 짝이다. */
const LOGIN_ERRORS = {
    not_configured: '서버에 카카오 로그인 설정이 없습니다. 배포 설정을 확인해 주세요.',
    denied: '로그인이 취소됐습니다.',
    invalid_state: '로그인 요청이 만료됐습니다. 다시 시도해 주세요.',
    rejected: '카카오가 로그인을 거절했습니다. 다시 시도해 주세요.',
    unavailable: '카카오를 부르지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
};

const DEFAULT_ERROR = '알 수 없는 오류가 생겼습니다.';

// ---------------------------------------------------------------- 상태

/** 서버에서 받은 전체 목록. 필터는 이 배열 위에서만 돌아 서버를 다시 부르지 않는다. */
let links = [];

/** 지금 편집 중인 항목. 새로 만들기면 null. */
let editing = null;

/**
 * 업로드 세대. 파일을 연속으로 고르거나 올리는 중에 편집기를 다시 열면 요청이 겹친다.
 *
 * 늦게 끝난 앞 요청이 지금 폼의 thumbnailUrl 을 앞 파일 주소로 덮으면, 화면에는 방금 고른 이미지가
 * 보이는데 저장되는 값은 다른 것이 된다. 시작할 때 세대를 올리고, 끝난 뒤 세대가 그대로일 때만 반영한다.
 */
let thumbUploadGeneration = 0;

const $ = (id) => document.getElementById(id);

// ---------------------------------------------------------------- 토큰

/**
 * 로그인 콜백이 남긴 프래그먼트를 거둔다.
 *
 * 읽은 즉시 주소창에서 지운다 — 남겨 두면 새로고침·북마크·화면 공유에 토큰이 그대로 따라다닌다.
 * history.replaceState 라 뒤로 가기 기록에도 안 남는다.
 */
function harvestFragment() {
    if (!location.hash || location.hash.length < 2) {
        return null;
    }
    const params = new URLSearchParams(location.hash.slice(1));
    history.replaceState(null, '', location.pathname + location.search);

    const token = params.get('access_token');
    if (token) {
        sessionStorage.setItem(TOKEN_KEY, token);
        return null;
    }
    return params.get('error');
}

/**
 * JWT 본문을 읽는다 — 화면에 무엇을 그릴지 정하려는 것뿐이다.
 *
 * **서명을 확인하지 않는다.** 확인할 이유가 없다: 토큰을 위조해 봐야 API 가 거절한다. 여기서 읽은 값은
 * 권한의 근거가 아니라 "로그인 버튼을 보여줄까 목록을 보여줄까" 의 근거다. 진짜 판정은 서버가 한다.
 */
function readClaims(token) {
    try {
        const payload = token.split('.')[1];
        // JWT 는 base64url 이라 표준 base64 로 바꿔 푼다. atob 는 바이트를 주므로 UTF-8 로 다시 읽는다 —
        // 지금 claim 은 전부 ASCII 지만, 한글이 실리는 날 조용히 깨지는 자리다.
        const bytes = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        const percent = Array.from(bytes, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join('');
        return JSON.parse(decodeURIComponent(percent));
    } catch (e) {
        return null;
    }
}

function logout() {
    sessionStorage.removeItem(TOKEN_KEY);
    location.reload();
}

// ---------------------------------------------------------------- 호출

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

/**
 * 모든 응답이 공통 래퍼({status, data, detail, code, pageResponse})로 온다.
 *
 * 401 은 토큰이 죽은 것이라 그 자리에서 로그인 화면으로 되돌린다 — 화면마다 따로 처리하면 어떤 경로는
 * 빈 목록을 보여주고 사람은 "데이터가 없다" 고 오해한다.
 */
async function call(path, options = {}) {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const response = await fetch(path, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
            ...(options.headers || {}),
        },
    });

    if (response.status === 401) {
        logout();
        throw new ApiError('로그인이 만료됐습니다.', 401);
    }

    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new ApiError((body && body.detail) || DEFAULT_ERROR, response.status);
    }
    return body;
}

/**
 * 목록 전체를 가져온다 — **페이지를 끝까지 돈다.**
 *
 * 필터가 브라우저에서 도는데 첫 페이지만 받으면 101번째 항목이 조용히 사라진다. 목록이 커져도 백오피스는
 * 한 사람이 하루 몇 번 여는 화면이라, 정확한 쪽을 고른다.
 */
async function fetchAll() {
    const collected = [];
    let page = 0;
    let totalPages = 1;
    while (page < totalPages) {
        const body = await call(`${LINKS_API}?page=${page}&size=${PAGE_SIZE}`);
        collected.push(...(body.data || []));
        totalPages = (body.pageResponse && body.pageResponse.totalPages) || 1;
        page += 1;
    }
    return collected;
}

// ---------------------------------------------------------------- 목록

function filtered() {
    const surface = $('filter-surface').value;
    const published = $('filter-published').value;
    return links.filter((link) => {
        if (surface && !(link.surfaces || []).includes(surface)) {
            return false;
        }
        if (published && String(link.published) !== published) {
            return false;
        }
        return true;
    });
}

/** 필터가 걸려 있으면 화면의 순서가 전체 순서가 아니다 — 그때는 끌어서 옮기지 못하게 한다. */
function reorderable() {
    return !$('filter-surface').value && !$('filter-published').value;
}

function periodText(link) {
    if (link.alwaysOn) {
        return '상시';
    }
    const from = link.startsOn || '';
    const to = link.endsOn || '';
    return from || to ? `${from} ~ ${to}` : '-';
}

function renderList() {
    const rows = $('rows');
    const visible = filtered();
    const canReorder = reorderable();

    rows.innerHTML = '';
    visible.forEach((link) => {
        const tr = document.createElement('tr');
        tr.dataset.id = String(link.id);
        tr.draggable = canReorder;
        if (!link.published) {
            tr.classList.add('is-unpublished');
        }

        tr.appendChild(cell(canReorder ? '⠿' : '', 'drag'));
        tr.appendChild(thumbCell(link.thumbnailUrl));
        tr.appendChild(titleCell(link));
        tr.appendChild(cell(periodText(link), 'period'));
        tr.appendChild(cell((link.surfaces || []).map((s) => SURFACE_LABELS[s] || s).join(' · '), 'surface'));
        tr.appendChild(cell(String(link.displayOrder), 'order'));
        tr.appendChild(stateCell(link.published));
        tr.appendChild(editCell(link));
        rows.appendChild(tr);
    });

    $('count').textContent = `${visible.length}건 / 전체 ${links.length}건`;
    $('reorder-hint').hidden = false;
    $('reorder-hint').textContent = canReorder
        ? '끌어서 순서를 바꾸면 그 자리에서 저장됩니다.'
        : '필터가 걸려 있어 순서를 바꿀 수 없습니다 — 보이는 순서가 전체 순서가 아닙니다.';
}

function cell(text, className) {
    const td = document.createElement('td');
    td.textContent = text;
    if (className) {
        td.className = className;
    }
    return td;
}

function thumbCell(url) {
    const td = document.createElement('td');
    td.className = 'thumb';
    if (url) {
        const img = document.createElement('img');
        img.src = url;
        img.alt = '';
        img.loading = 'lazy';
        td.appendChild(img);
    } else {
        td.appendChild(document.createTextNode('-'));
    }
    return td;
}

function titleCell(link) {
    const td = document.createElement('td');
    const title = document.createElement('strong');
    title.textContent = link.title;
    const chip = document.createElement('span');
    chip.className = 'row-chip';
    chip.textContent = link.chipText;
    td.appendChild(title);
    td.appendChild(document.createElement('br'));
    td.appendChild(chip);
    if (link.updatedBy) {
        const by = document.createElement('small');
        by.className = 'by';
        by.textContent = ` 마지막 수정 ${link.updatedBy}`;
        td.appendChild(by);
    }
    return td;
}

function stateCell(published) {
    const td = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = published ? 'badge on' : 'badge off';
    badge.textContent = published ? '노출' : '숨김';
    td.appendChild(badge);
    return td;
}

function editCell(link) {
    const td = document.createElement('td');
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'ghost';
    button.textContent = '편집';
    button.addEventListener('click', () => openEditor(link));
    td.appendChild(button);
    return td;
}

async function reload() {
    setListMessage('');
    try {
        links = await fetchAll();
        links.sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id);
        renderList();
    } catch (error) {
        setListMessage(error.message);
    }
}

function setListMessage(text) {
    const node = $('list-message');
    node.textContent = text;
    node.hidden = !text;
}

// ---------------------------------------------------------------- 드래그 정렬

let dragging = null;

function bindDragAndDrop() {
    const rows = $('rows');

    rows.addEventListener('dragstart', (event) => {
        const tr = event.target.closest('tr');
        if (!tr || !tr.draggable) {
            return;
        }
        dragging = tr;
        tr.classList.add('is-dragging');
        event.dataTransfer.effectAllowed = 'move';
        // Firefox 는 데이터가 없으면 드래그를 시작조차 하지 않는다.
        event.dataTransfer.setData('text/plain', tr.dataset.id);
    });

    rows.addEventListener('dragover', (event) => {
        if (!dragging) {
            return;
        }
        event.preventDefault();
        const over = event.target.closest('tr');
        if (!over || over === dragging) {
            return;
        }
        // 커서가 행의 위쪽 절반이면 그 앞에, 아래쪽이면 뒤에 놓는다.
        const box = over.getBoundingClientRect();
        const before = event.clientY < box.top + box.height / 2;
        over.parentNode.insertBefore(dragging, before ? over : over.nextSibling);
    });

    rows.addEventListener('dragend', async () => {
        if (!dragging) {
            return;
        }
        dragging.classList.remove('is-dragging');
        dragging = null;
        await persistOrder();
    });
}

/**
 * 화면 순서를 서버에 반영한다 — **바뀐 것만** 보낸다.
 *
 * 전부 다시 쓰면 한 번 끄는 데 요청이 목록 크기만큼 나가고, 감사 흔적(updated_by)도 손대지 않은 항목까지
 * 내 이름으로 덮인다.
 */
async function persistOrder() {
    const order = Array.from($('rows').children).map((tr) => Number(tr.dataset.id));
    const changed = [];

    order.forEach((id, index) => {
        const link = links.find((candidate) => candidate.id === id);
        if (link && link.displayOrder !== index) {
            link.displayOrder = index;
            changed.push(link);
        }
    });

    if (!changed.length) {
        return;
    }

    setListMessage(`순서 저장 중 (${changed.length}건)…`);
    try {
        // 순차로 보낸다. 같은 표를 동시에 고치면 마지막 응답이 어느 순서를 남겼는지 알 수 없다.
        for (const link of changed) {
            await call(`${LINKS_API}/${link.id}`, {method: 'PATCH', body: JSON.stringify(toRequest(link))});
        }
        setListMessage('');
        await reload();
    } catch (error) {
        setListMessage(`순서를 저장하지 못했습니다 — ${error.message}`);
        await reload();
    }
}

// ---------------------------------------------------------------- 편집

/** 화면 값 → 서버 요청. 빈 문자열은 null 로 바꾼다 — 서버에서 "안 보냄" 과 같은 뜻이어야 한다. */
function toRequest(link) {
    return {
        title: link.title,
        chipText: link.chipText,
        description: emptyToNull(link.description),
        linkUrl: link.linkUrl,
        thumbnailUrl: emptyToNull(link.thumbnailUrl),
        startsOn: emptyToNull(link.startsOn),
        endsOn: emptyToNull(link.endsOn),
        alwaysOn: Boolean(link.alwaysOn),
        surfaces: link.surfaces || [],
        displayOrder: Number(link.displayOrder) || 0,
        published: Boolean(link.published),
    };
}

function emptyToNull(value) {
    return value === undefined || value === null || value === '' ? null : value;
}

function openEditor(link) {
    editing = link;
    const form = $('editor-form');

    $('editor-title').textContent = link ? '큐레이션 링크 편집' : '큐레이션 링크 만들기';
    $('delete').hidden = !link;
    setEditorError('');

    form.title.value = (link && link.title) || '';
    form.chipText.value = (link && link.chipText) || '';
    form.description.value = (link && link.description) || '';
    form.linkUrl.value = (link && link.linkUrl) || '';
    form.thumbnailUrl.value = (link && link.thumbnailUrl) || '';
    form.startsOn.value = (link && link.startsOn) || '';
    form.endsOn.value = (link && link.endsOn) || '';
    form.alwaysOn.checked = Boolean(link && link.alwaysOn);
    form.displayOrder.value = link ? link.displayOrder : 0;
    form.published.checked = Boolean(link && link.published);

    const chosen = new Set((link && link.surfaces) || []);
    surfaceInputs().forEach((input) => {
        input.checked = chosen.has(input.value);
    });

    resetThumbTabs(Boolean(link && link.thumbnailUrl));
    refreshPreview();
    $('editor').showModal();
}

function surfaceInputs() {
    return Array.from($('editor-form').querySelectorAll('input[name="surfaces"]'));
}

function readForm() {
    const form = $('editor-form');
    return {
        title: form.title.value.trim(),
        chipText: form.chipText.value.trim(),
        description: form.description.value.trim(),
        linkUrl: form.linkUrl.value.trim(),
        thumbnailUrl: form.thumbnailUrl.value.trim(),
        startsOn: form.startsOn.value,
        endsOn: form.endsOn.value,
        alwaysOn: form.alwaysOn.checked,
        surfaces: surfaceInputs().filter((input) => input.checked).map((input) => input.value),
        displayOrder: Number(form.displayOrder.value) || 0,
        published: form.published.checked,
    };
}

async function save() {
    const draft = readForm();
    setEditorError('');

    // 서버도 같은 것을 보지만, 여기서 먼저 잡으면 왕복 없이 그 자리에서 고칠 수 있다.
    if (!draft.surfaces.length) {
        setEditorError('내릴 면을 하나 이상 고르세요.');
        return;
    }

    try {
        if (editing) {
            await call(`${LINKS_API}/${editing.id}`, {method: 'PATCH', body: JSON.stringify(toRequest(draft))});
        } else {
            await call(LINKS_API, {method: 'POST', body: JSON.stringify(toRequest(draft))});
        }
        $('editor').close();
        await reload();
    } catch (error) {
        setEditorError(error.message);
    }
}

async function remove() {
    if (!editing || !confirm(`"${editing.title}" 을(를) 지웁니다. 되돌릴 수 없습니다.`)) {
        return;
    }
    try {
        await call(`${LINKS_API}/${editing.id}`, {method: 'DELETE'});
        $('editor').close();
        await reload();
    } catch (error) {
        setEditorError(error.message);
    }
}

function setEditorError(text) {
    const node = $('editor-error');
    node.textContent = text;
    node.hidden = !text;
}

// ---------------------------------------------------------------- 썸네일 업로드

/**
 * 편집기를 열 때마다 업로드 칸을 처음 상태로 되돌린다.
 *
 * 되돌리지 않으면 앞 항목에서 고른 파일과 "올렸습니다" 문구가 다음 항목에 그대로 남는다 — 실제로는
 * 아무것도 안 올렸는데 올린 것처럼 보인다.
 *
 * 이미 주소가 있는 항목은 '주소 붙여넣기' 로 연다. 지금 무엇이 걸려 있는지가 먼저 보여야 고칠지 말지를
 * 정할 수 있다.
 */
function resetThumbTabs(hasUrl) {
    thumbUploadGeneration += 1; // 편집기를 다시 열면 앞 업로드의 결과는 이 폼의 것이 아니다
    $('thumb-file').value = '';
    setThumbStatus('');
    showThumbTab(hasUrl ? 'url' : 'upload');
}

function showThumbTab(which) {
    const onUpload = which === 'upload';
    $('thumb-tab-upload').classList.toggle('is-on', onUpload);
    $('thumb-tab-url').classList.toggle('is-on', !onUpload);
    $('thumb-panel-upload').hidden = !onUpload;
    $('thumb-panel-url').hidden = onUpload;
}

function setThumbStatus(message, isError = false) {
    const status = $('thumb-status');
    status.textContent = message;
    status.classList.toggle('is-over', isError);
}

/**
 * 고른 파일을 S3 로 **직접** 올린다.
 *
 * 서버는 이 한 건에만 쓰는 서명된 주소만 내주고 바이트는 받지 않는다 — EC2 한 대에 MySQL 이 동거하는
 * 형편이라 업로드를 앱 메모리로 받을 여유가 없다.
 *
 * 서명에 종류와 크기가 들어 있어, 여기서 보내는 Content-Type 과 실제 바이트 수가 발급 때와 달라지면
 * S3 가 거절한다. 그래서 같은 File 객체로 둘 다 만든다.
 */
async function uploadThumbnail(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
        return;
    }

    // 서버도 같은 것을 보지만, 여기서 먼저 잡으면 5MB 를 올려 보고 나서야 거절당하는 일이 없다.
    if (file.size > MAX_THUMB_BYTES) {
        setThumbStatus('이미지가 너무 큽니다. 5MB 이하로 올려 주세요.', true);
        event.target.value = '';
        return;
    }

    const generation = (thumbUploadGeneration += 1);
    setThumbStatus('올리는 중…');
    try {
        const ticket = await call(UPLOADS_API, {
            method: 'POST',
            body: JSON.stringify({contentType: file.type, contentLength: file.size}),
        });

        // 서명된 주소로는 우리 토큰을 보내지 않는다 — 인증은 서명 자체가 하고, Authorization 헤더가
        // 붙으면 S3 가 서명과 어긋난 요청으로 보고 거절한다. 그래서 call() 을 쓰지 않는다.
        const put = await fetch(ticket.data.uploadUrl, {
            method: 'PUT',
            headers: {'Content-Type': file.type},
            body: file,
        });
        if (!put.ok) {
            throw new Error(`S3 ${put.status}`);
        }

        if (generation !== thumbUploadGeneration) {
            return; // 그 사이에 다른 파일을 고르거나 편집기를 다시 열었다 — 이 결과는 버린다
        }
        $('editor-form').thumbnailUrl.value = ticket.data.publicUrl;
        setThumbStatus('올렸습니다. 저장을 눌러야 반영됩니다.');
        refreshPreview();
    } catch (error) {
        if (generation !== thumbUploadGeneration) {
            return; // 지난 업로드의 실패로 지금 화면에 오류를 띄우지 않는다
        }
        // 사유를 그대로 보여준다 — 저장소 설정이 없는 것(502)과 종류·크기 문제(400)는 다음 행동이 다르다.
        setThumbStatus(error.message || DEFAULT_ERROR, true);
        event.target.value = '';
    }
}

/** 앱 카드 미리보기 — 칩 문구가 어디서 접히는지 등록 화면에서 바로 보이게 하는 것이 목적이다. */
function refreshPreview() {
    const draft = readForm();
    $('preview-chip').textContent = draft.chipText || '칩 문구';
    $('preview-title').textContent = draft.title || '제목';
    $('preview-desc').textContent = draft.description;
    $('preview-desc').hidden = !draft.description;

    const thumb = $('preview-thumb');
    thumb.style.backgroundImage = draft.thumbnailUrl ? `url("${CSS.escape(draft.thumbnailUrl)}")` : '';
    thumb.classList.toggle('is-empty', !draft.thumbnailUrl);

    const used = draft.chipText.length;
    const counter = $('chip-counter');
    counter.textContent = `${used} / ${MAX_CHIP_TEXT}`;
    counter.classList.toggle('is-over', used > MAX_CHIP_TEXT);
}

// ---------------------------------------------------------------- 시작

function showOnly(sectionId) {
    ['gate-login', 'gate-forbidden', 'console'].forEach((id) => {
        $(id).hidden = id !== sectionId;
    });
}

function boot() {
    const loginError = harvestFragment();
    const token = sessionStorage.getItem(TOKEN_KEY);

    if (!token) {
        showOnly('gate-login');
        if (loginError) {
            const node = $('login-error');
            node.textContent = LOGIN_ERRORS[loginError] || DEFAULT_ERROR;
            node.hidden = false;
        }
        return;
    }

    const claims = readClaims(token);
    const roles = (claims && claims.roles) || [];
    if (!roles.includes(ADMIN_ROLE)) {
        showOnly('gate-forbidden');
        $('my-user-id').textContent = (claims && claims.sub) || '(사용자 ID 를 읽지 못했습니다)';
        return;
    }

    showOnly('console');
    $('who').hidden = false;
    $('who-label').textContent = '어드민으로 로그인됨';
    reload();
}

function bind() {
    $('logout').addEventListener('click', logout);
    $('logout-forbidden').addEventListener('click', logout);
    $('login').addEventListener('click', () => sessionStorage.removeItem(TOKEN_KEY));
    $('reload').addEventListener('click', reload);
    $('new-link').addEventListener('click', () => openEditor(null));
    $('filter-surface').addEventListener('change', renderList);
    $('filter-published').addEventListener('change', renderList);

    $('save').addEventListener('click', save);
    $('delete').addEventListener('click', remove);
    $('cancel').addEventListener('click', () => $('editor').close());
    $('editor-close').addEventListener('click', () => $('editor').close());
    $('editor-form').addEventListener('input', refreshPreview);
    $('thumb-tab-upload').addEventListener('click', () => showThumbTab('upload'));
    $('thumb-tab-url').addEventListener('click', () => showThumbTab('url'));
    $('thumb-file').addEventListener('change', uploadThumbnail);

    $('copy-user-id').addEventListener('click', async () => {
        await navigator.clipboard.writeText($('my-user-id').textContent);
        $('copy-user-id').textContent = '복사됨';
    });

    bindDragAndDrop();
}

document.addEventListener('DOMContentLoaded', () => {
    bind();
    boot();
});
