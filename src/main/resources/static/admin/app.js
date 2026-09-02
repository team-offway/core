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

const POLICIES_API = '/api/v1/admin/policies';

/**
 * 7대 혜택 분류 — PolicyType 과 짝이다.
 *
 * **뱃지 문구를 여기 적어 두는 것은 미리보기 때문이다.** 실제 문구는 서버가 badgeText 로 내려주므로
 * 저장된 정책은 그 값을 쓴다. 여기 값은 아직 저장 전인 폼에서 "이 분류를 고르면 뱃지가 뭐가 되나" 를
 * 보여줄 때만 쓴다 — 서버 값과 어긋나면 화면이 거짓말을 하므로 PolicyType 을 고칠 때 함께 고친다.
 */
const POLICY_TYPES = [
    {value: 'DIGITAL_TOURIST_CARD', name: '디지털관광주민증', badge: '디지털관광주민증', scope: '인구감소지역 89곳'},
    {value: 'REGIONAL_VOUCHER', name: '지역사랑 휴가지원(반값여행)', badge: '여행경비 50% 환급', scope: '참여 지자체만'},
    {value: 'STAY_FESTA', name: '숙박세일페스타', badge: '숙박 할인', scope: '비수도권 인구감소지역'},
    {value: 'WORKER_VACATION', name: '근로자 휴가지원', badge: '근로자 휴가비 지원', scope: '인구감소지역 89곳'},
    {value: 'RAIL_DISCOUNT', name: 'KTX·SRT 할인', badge: 'KTX·SRT 할인', scope: '인구감소지역 89곳'},
    {value: 'LOCAL_TOURISM', name: '로컬100·관광두레', badge: '로컬100·관광두레', scope: '인구감소지역 89곳'},
    {value: 'RURAL', name: '농촌체험·치유관광', badge: '농촌체험·치유관광', scope: '인구감소지역 89곳'},
];

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

/** 정책 목록·편집 대상. 링크와 같은 구조를 따로 든다 — 두 탭이 서로의 상태를 밟지 않게. */
let policies = [];
let editingPolicy = null;

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

    $('editor-title').textContent = link ? '바로가기 링크 편집' : '바로가기 링크 만들기';
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

    // 파일을 고른 순간 세대를 올린다 — 크기 검사보다 **앞**이어야 한다. 뒤에 두면 5MB 초과 파일을
    // 골라 여기서 되돌아갈 때 앞 업로드의 세대가 그대로 살아, 그것이 끝나면서 "올렸습니다" 와 주소를
    // 지금 폼에 얹는다. 방금 거절당한 파일이 올라간 것처럼 보인다.
    const generation = (thumbUploadGeneration += 1);

    // 서버도 같은 것을 보지만, 여기서 먼저 잡으면 5MB 를 올려 보고 나서야 거절당하는 일이 없다.
    if (file.size > MAX_THUMB_BYTES) {
        setThumbStatus('이미지가 너무 큽니다. 5MB 이하로 올려 주세요.', true);
        event.target.value = '';
        return;
    }

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


// ---------------------------------------------------------------- 정책 탭

/** 상단 탭만 — 편집기 안의 썸네일 탭과 구분한다. */
function consoleTabs() {
    return document.querySelectorAll('nav.tabs .tab');
}

/**
 * 화면 이름 → 패널 id. **여기 한 줄이 화면 하나다.**
 *
 * 예전에는 showTab 안에 `$('tab-links').hidden = ...` 을 화면 수만큼 늘어놓았다. 화면이 셋이 되면서
 * 늘어놓는 방식이 한계에 왔고(#398), 넷째(#403 연동 제어)가 곧 붙는다. 표로 두면 새 화면이 이 객체에
 * 한 줄 더하는 것으로 끝난다.
 */
const PANELS = {
    links: 'tab-links',
    policies: 'tab-policies',
    externals: 'tab-externals',
};

/** 그 화면을 처음 열 때 한 번 채운다. 각 화면은 자기 데이터를 자기가 들고 있다. */
const LOADERS = {
    policies: () => policies.length || reloadPolicies(),
    externals: () => externals || reloadExternals(),
};

/** 화면 전환 — 패널만 갈아 끼운다. */
function showTab(name) {
    // **nav 로 좁힌다.** 썸네일 칸에도 class="tabs" 가 있어, 좁히지 않으면 그 버튼을 누를 때
    // showTab(undefined) 가 돌아 패널이 전부 숨는다.
    consoleTabs().forEach((tab) => {
        tab.classList.toggle('is-active', tab.dataset.tab === name);
    });
    Object.entries(PANELS).forEach(([tab, panelId]) => {
        $(panelId).hidden = tab !== name;
    });
    if (LOADERS[name]) {
        LOADERS[name]();
    }
}

function typeOf(value) {
    return POLICY_TYPES.find((t) => t.value === value) || null;
}

/** 분류 select 를 채운다 — 목록 필터와 편집 폼이 같은 목록을 쓴다. */
function fillTypeOptions() {
    POLICY_TYPES.forEach((type) => {
        const filterOption = document.createElement('option');
        filterOption.value = type.value;
        filterOption.textContent = type.name;
        $('filter-type').appendChild(filterOption);

        const formOption = document.createElement('option');
        formOption.value = type.value;
        formOption.textContent = type.name;
        $('policy-type').appendChild(formOption);
    });
}

function filteredPolicies() {
    const type = $('filter-type').value;
    const verified = $('filter-verified').value;
    return policies.filter((policy) => {
        if (type && policy.type !== type) {
            return false;
        }
        if (verified && String(policy.verified) !== verified) {
            return false;
        }
        return true;
    });
}

function policyPeriodText(policy) {
    const from = policy.periodStart || '';
    const to = policy.periodEnd || '';
    if (!from && !to) {
        return '상시';
    }
    return `${from || '~'} ~ ${to || '끝없음'}`;
}

function renderPolicies() {
    const rows = $('policy-rows');
    const visible = filteredPolicies();

    rows.innerHTML = '';
    visible.forEach((policy) => {
        const tr = document.createElement('tr');
        if (!policy.verified) {
            tr.classList.add('is-unpublished');
        }

        const type = typeOf(policy.type);
        tr.appendChild(cell(type ? type.name : policy.type, 'type'));
        tr.appendChild(policyTitleCell(policy));
        tr.appendChild(cell(policyPeriodText(policy), 'period'));
        tr.appendChild(cell(policy.checkedOn || '-', 'checked'));
        tr.appendChild(verifiedCell(policy.verified));
        tr.appendChild(policyEditCell(policy));
        rows.appendChild(tr);
    });

    $('policy-count').textContent = `${visible.length}건 / 전체 ${policies.length}건`;
}

function policyTitleCell(policy) {
    const td = document.createElement('td');
    const name = document.createElement('strong');
    name.textContent = policy.name;
    const badge = document.createElement('span');
    badge.className = 'row-chip';
    badge.textContent = policy.badgeText;
    td.appendChild(name);
    td.appendChild(document.createElement('br'));
    td.appendChild(badge);
    if (policy.updatedBy) {
        const by = document.createElement('small');
        by.className = 'by';
        by.textContent = ` 마지막 수정 ${policy.updatedBy}`;
        td.appendChild(by);
    }
    return td;
}

function verifiedCell(verified) {
    const td = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = verified ? 'badge on' : 'badge off';
    badge.textContent = verified ? '노출' : '미검증';
    td.appendChild(badge);
    return td;
}

function policyEditCell(policy) {
    const td = document.createElement('td');
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'ghost';
    button.textContent = '편집';
    button.addEventListener('click', () => openPolicyEditor(policy));
    td.appendChild(button);
    return td;
}

async function reloadPolicies() {
    setPolicyMessage('');
    try {
        const collected = [];
        let page = 0;
        let totalPages = 1;
        while (page < totalPages) {
            const body = await call(`${POLICIES_API}?page=${page}&size=${PAGE_SIZE}`);
            collected.push(...(body.data || []));
            totalPages = (body.pageResponse && body.pageResponse.totalPages) || 1;
            page += 1;
        }
        policies = collected;
        renderPolicies();
    } catch (error) {
        setPolicyMessage(error.message);
    }
}

function setPolicyMessage(text) {
    const node = $('policy-message');
    node.textContent = text;
    node.hidden = !text;
}

// ---------------------------------------------------------------- 정책 편집

function openPolicyEditor(policy) {
    editingPolicy = policy;
    const form = $('policy-form');

    $('policy-editor-title').textContent = policy ? '지역 혜택 편집' : '지역 혜택 만들기';
    $('policy-delete').hidden = !policy;
    setPolicyError('');

    form.type.value = (policy && policy.type) || POLICY_TYPES[0].value;
    form.name.value = (policy && policy.name) || '';
    form.benefitDetail.value = (policy && policy.benefitDetail) || '';
    form.targetAudience.value = (policy && policy.targetAudience) || '';
    form.applyUrl.value = (policy && policy.applyUrl) || '';
    form.periodStart.value = (policy && policy.periodStart) || '';
    form.periodEnd.value = (policy && policy.periodEnd) || '';
    form.periodNote.value = (policy && policy.periodNote) || '';
    form.checkedOn.value = (policy && policy.checkedOn) || '';
    form.verified.checked = Boolean(policy && policy.verified);

    refreshPolicyPreview();
    $('policy-editor').showModal();
}

function readPolicyForm() {
    const form = $('policy-form');
    return {
        type: form.type.value,
        name: form.name.value.trim(),
        benefitDetail: form.benefitDetail.value.trim(),
        targetAudience: form.targetAudience.value.trim(),
        applyUrl: form.applyUrl.value.trim(),
        periodStart: form.periodStart.value,
        periodEnd: form.periodEnd.value,
        periodNote: form.periodNote.value.trim(),
        checkedOn: form.checkedOn.value,
        verified: form.verified.checked,
    };
}

/** 빈 문자열은 null 로 — 서버에서 "안 보냄" 과 같은 뜻이어야 한다. */
function toPolicyRequest(draft) {
    return {
        type: draft.type,
        name: draft.name,
        benefitDetail: emptyToNull(draft.benefitDetail),
        targetAudience: emptyToNull(draft.targetAudience),
        periodStart: emptyToNull(draft.periodStart),
        periodEnd: emptyToNull(draft.periodEnd),
        periodNote: emptyToNull(draft.periodNote),
        applyUrl: emptyToNull(draft.applyUrl),
        verified: Boolean(draft.verified),
        checkedOn: emptyToNull(draft.checkedOn),
    };
}

/**
 * https 이고 호스트가 있는 주소인가 — 서버의 `Policy.requireHttpsOrNull` 과 같은 판정이다.
 *
 * **접두사 비교가 아니라 파싱이다.** 앞자리만 보면 호스트 없는 `https://` 가 통과하고 대문자
 * `HTTPS://` 는 거절된다 — 스킴은 대소문자를 가리지 않는다.
 *
 * 서버가 다시 보므로 이 검사가 없어도 안전하다. 여기 두는 이유는 **왕복 없이 그 자리에서 고칠 수 있게**
 * 하는 것뿐이다.
 */
function isHttpsUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === 'https:' && Boolean(url.hostname);
    } catch (e) {
        return false;
    }
}

async function savePolicy() {
    const draft = readPolicyForm();
    setPolicyError('');

    // 서버도 같은 것을 보지만, 여기서 먼저 잡으면 왕복 없이 그 자리에서 고칠 수 있다.
    if (draft.periodStart && draft.periodEnd && draft.periodStart > draft.periodEnd) {
        setPolicyError('시작일이 종료일보다 늦습니다. 그대로 두면 뱃지가 영영 안 뜹니다.');
        return;
    }
    if (draft.applyUrl && !isHttpsUrl(draft.applyUrl)) {
        setPolicyError('신청 주소는 https 로 시작하는 올바른 주소여야 합니다.');
        return;
    }

    try {
        const body = JSON.stringify(toPolicyRequest(draft));
        if (editingPolicy) {
            await call(`${POLICIES_API}/${editingPolicy.id}`, {method: 'PATCH', body});
        } else {
            await call(POLICIES_API, {method: 'POST', body});
        }
        $('policy-editor').close();
        await reloadPolicies();
    } catch (error) {
        setPolicyError(error.message);
    }
}

async function removePolicy() {
    if (!editingPolicy || !confirm(`"${editingPolicy.name}" 을(를) 지웁니다. 되돌릴 수 없습니다.`)) {
        return;
    }
    try {
        await call(`${POLICIES_API}/${editingPolicy.id}`, {method: 'DELETE'});
        $('policy-editor').close();
        await reloadPolicies();
    } catch (error) {
        setPolicyError(error.message);
    }
}

function setPolicyError(text) {
    const node = $('policy-error');
    node.textContent = text;
    node.hidden = !text;
}

/**
 * 미리보기 — **분류가 무엇을 함께 정하는지** 보여주는 것이 목적이다.
 *
 * 뱃지 문구와 대상 지역이 분류에 묶여 있는데, 폼에서는 select 하나로만 보인다. 고르는 순간 무엇이
 * 따라 바뀌는지 옆에서 말해 주지 않으면 어드민이 그 사실을 모른 채 분류를 바꾼다.
 */
function refreshPolicyPreview() {
    const draft = readPolicyForm();
    const type = typeOf(draft.type);

    $('policy-preview-badge').textContent = type ? type.badge : draft.type;
    $('policy-preview-name').textContent = draft.name || '정책명';
    $('policy-preview-detail').textContent = draft.benefitDetail;
    $('policy-preview-detail').hidden = !draft.benefitDetail;

    const scope = type ? `대상 지역: ${type.scope}` : '';
    $('policy-type-note').textContent = scope;
    $('policy-preview-note').textContent = draft.verified
        ? scope
        : '검증을 켜지 않아 앱에 나가지 않습니다.';
}


// ---------------------------------------------------------------- 외부 API

/**
 * 마지막으로 받은 연동 현황(#398).
 *
 * 화면을 다시 열 때 다시 부르지 않는다. 이 값은 배치가 채우는 것이라 몇 초 사이에 바뀌지 않고,
 * 새로 보고 싶으면 새로고침이 있다.
 */
let externals = null;

const DAY_MS = 24 * 60 * 60 * 1000;

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (text !== undefined && text !== null) {
        node.textContent = text;
    }
    return node;
}

function num(value) {
    return Number(value || 0).toLocaleString('ko-KR');
}

function setExternalMessage(text) {
    const node = $('external-message');
    node.textContent = text || '';
    node.hidden = !text;
}

async function reloadExternals() {
    setExternalMessage('불러오는 중…');
    try {
        const days = $('filter-days').value;
        const body = await call(`/api/v1/admin/external-apis?days=${encodeURIComponent(days)}`);
        externals = body.data;
        renderExternals();
        setExternalMessage(null);
    } catch (error) {
        // 실패를 조용히 넘기지 않는다 — 빈 화면은 "연동이 없다" 로 읽힌다.
        externals = null;
        $('external-body').hidden = true;
        setExternalMessage(error.message || DEFAULT_ERROR);
    }
}

function renderExternals() {
    if (!externals) {
        return;
    }
    $('external-body').hidden = false;
    $('external-count').textContent =
        `연동 ${externals.apis.length}개 · ${externals.from} ~ ${externals.to} (${externals.days}일)`;
    renderApiList();
    renderDailyBars();
    renderBatches();
}

function renderApiList() {
    const list = $('api-list');
    list.innerHTML = '';
    externals.apis.forEach((api) => list.appendChild(apiCard(api)));
}

function apiCard(api) {
    const card = el('article', 'api-card');
    if (api.periodTotal === 0) {
        // 기간 내 한 번도 안 부른 연동. 지우지 않고 옅게 둔다 — 안 쓰는 연동이 보여야 한다.
        card.classList.add('is-idle');
    }

    const head = el('div', 'api-head');
    head.appendChild(el('b', null, api.label));
    head.appendChild(el('code', null, api.name));
    head.appendChild(el('span', 'spacer'));
    head.appendChild(el('span', 'api-today',
        `오늘 ${num(api.todayUsed)} / ${num(api.dailyLimit)}`));
    card.appendChild(head);

    card.appendChild(meter(api.todayUsedRate));

    const totals = el('p', 'api-totals');
    totals.appendChild(el('span', null, `${externals.days}일 합계 ${num(api.periodTotal)}`));
    totals.appendChild(el('span', 'sep', '·'));
    totals.appendChild(el('span', null, `배치 ${num(api.batchTotal)}`));
    totals.appendChild(el('span', 'sep', '·'));
    // 사용자 요청 몫을 강조한다 — "서비스가 요청마다 실제로 부른다" 를 보이는 값이다.
    totals.appendChild(el('b', null, `사용자 요청 ${num(api.requestTotal)}`));
    card.appendChild(totals);

    if (api.callers.length) {
        card.appendChild(callerLine(api.callers));
    }
    if (api.flows.length) {
        card.appendChild(flowList(api.flows));
    }
    card.appendChild(apiControls(api));
    return card;
}

/**
 * 연동 하나를 조절한다(#403).
 *
 * 캐시를 끄면 그 연동은 매번 실호출한다 — 한도를 더 태우는 대신 값이 늘 최신이 된다. 그래서 누르기
 * 전에 지금 무엇이 바뀌는지 곁에 적어 둔다.
 */
function apiControls(api) {
    const row = el('div', 'api-controls');

    const cache = el('label', 'switch');
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.checked = api.cacheEnabled;
    box.addEventListener('change', () => saveApi(api, {cacheEnabled: box.checked, batchLimit: api.batchLimit}));
    cache.appendChild(box);
    cache.appendChild(el('span', null, '캐시 사용'));
    row.appendChild(cache);

    const limit = el('label', 'switch');
    limit.appendChild(el('span', null, '배치 상한'));
    const input = document.createElement('input');
    input.type = 'number';
    input.className = 'limit';
    input.min = '0';
    input.max = String(api.dailyLimit);
    input.placeholder = '무제한';
    input.value = api.batchLimit === null || api.batchLimit === undefined ? '' : String(api.batchLimit);
    input.addEventListener('change', () => saveApi(api, {
        cacheEnabled: api.cacheEnabled,
        batchLimit: input.value === '' ? null : Number(input.value),
    }));
    limit.appendChild(input);
    row.appendChild(limit);

    row.appendChild(el('span', 'spacer'));
    if (!api.settingDefault) {
        // 기본에서 벗어난 것을 드러낸다 — 안 그러면 몇 달 뒤 "왜 이게 꺼져 있지" 가 된다.
        row.appendChild(el('span', 'touched', '기본값 아님'));
    }
    return row;
}

async function saveApi(api, body) {
    await applySetting(`/api/v1/admin/external-apis/${encodeURIComponent(api.name)}`, body);
}

async function saveBatch(name, enabled) {
    await applySetting(`/api/v1/admin/external-apis/batches/${encodeURIComponent(name)}`, {enabled});
}

/** 응답이 현황 전체라 그대로 다시 그린다 — 바꾼 한 줄만 고치면 소진율·예상 콜 수가 어긋난다. */
async function applySetting(path, body) {
    setExternalMessage('저장 중…');
    try {
        const result = await call(path, {method: 'PATCH', body: JSON.stringify(body)});
        externals = result.data;
        renderExternals();
        setExternalMessage(null);
    } catch (error) {
        setExternalMessage(error.message || DEFAULT_ERROR);
        // 실패했으면 화면이 거짓말하지 않게 서버 값으로 되돌린다.
        reloadExternals();
    }
}

/** 소진율 막대. 70% 를 넘으면 색이 바뀐다 — 디스코드 경보가 우는 지점과 같다. */
function meter(rate) {
    const bar = el('div', 'meter');
    const fill = el('div', 'meter-fill');
    fill.style.width = `${Math.max(0, Math.min(100, rate))}%`;
    if (rate >= 90) {
        fill.classList.add('is-danger');
    } else if (rate >= 70) {
        fill.classList.add('is-warn');
    }
    bar.appendChild(fill);
    return bar;
}

function callerLine(callers) {
    const line = el('p', 'api-callers');
    callers.slice(0, 5).forEach((share, index) => {
        if (index > 0) {
            line.appendChild(el('span', 'sep', '·'));
        }
        const chip = el('span', share.fromRequest ? 'caller is-request' : 'caller');
        chip.appendChild(el('span', 'caller-name', share.caller));
        chip.appendChild(el('span', 'caller-count', num(share.count)));
        line.appendChild(chip);
    });
    if (callers.length > 5) {
        line.appendChild(el('span', 'sep', '·'));
        line.appendChild(el('span', 'caller', `외 ${callers.length - 5}`));
    }
    return line;
}

/** 어느 화면이 이 API 를 어떻게 쓰나. 서버의 DataFlow 가 소유하는 값이다. */
function flowList(flows) {
    const wrap = el('ul', 'flows');
    flows.forEach((flow) => {
        const item = el('li');
        item.appendChild(el('span', `mode mode-${flow.modeName}`, flow.mode));
        item.appendChild(el('b', null, flow.screen));
        item.appendChild(el('span', 'flow-note', flow.note));
        item.title = `${flow.path}\n${flow.modeDetail}`;
        wrap.appendChild(item);
    });
    return wrap;
}

function renderDailyBars() {
    const wrap = $('daily-bars');
    wrap.innerHTML = '';
    const peak = Math.max(1, ...externals.daily.map((day) => day.total));

    $('daily-note').textContent = externals.daily.some((day) => day.total > 0)
        ? `가장 많은 날 ${num(peak)}회. 월배치가 도는 날이 튑니다 — 매월 1일과 5일을 보세요.`
        : '이 기간에 기록이 없습니다.';

    externals.daily.forEach((day) => {
        const row = el('div', 'bar-row');
        row.appendChild(el('span', 'bar-date', day.date.slice(5)));
        const track = el('div', 'bar-track');
        const fill = el('div', 'bar-fill');
        fill.style.width = `${(day.total / peak) * 100}%`;
        track.appendChild(fill);
        row.appendChild(track);
        row.appendChild(el('span', 'bar-count', num(day.total)));
        row.title = Object.entries(day.counts)
            .sort((a, b) => b[1] - a[1])
            .map(([api, count]) => `${api} ${num(count)}`)
            .join('\n') || '기록 없음';
        wrap.appendChild(row);
    });
}

function renderBatches() {
    const rows = $('batch-rows');
    rows.innerHTML = '';
    if (!externals.batches.length) {
        const tr = document.createElement('tr');
        const td = cell('아직 기록된 배치가 없습니다.', null);
        td.colSpan = 4;
        tr.appendChild(td);
        rows.appendChild(tr);
        return;
    }
    externals.batches.forEach((batch) => {
        const tr = document.createElement('tr');
        if (!batch.enabled) {
            tr.classList.add('is-unpublished');
        }
        tr.appendChild(cell(batch.name, null));
        tr.appendChild(cell(batchWhen(batch.lastRunAt), 'period'));

        const state = document.createElement('td');
        const badge = el('span', batch.enabled ? 'badge on' : 'badge off', batch.enabled ? '동작' : '멈춤');
        state.appendChild(badge);
        tr.appendChild(state);

        const action = document.createElement('td');
        const button = el('button', batch.enabled ? 'danger' : 'ghost', batch.enabled ? '멈추기' : '다시 돌리기');
        button.type = 'button';
        button.addEventListener('click', () => saveBatch(batch.name, !batch.enabled));
        action.appendChild(button);
        tr.appendChild(action);
        rows.appendChild(tr);
    });
}

/** 마지막 실행을 "언제였나" 로 읽히게. 날짜만 보면 오래된 것인지 한눈에 안 들어온다. */
function batchWhen(value) {
    if (!value) {
        return '-';
    }
    const at = new Date(value);
    const days = Math.floor((Date.now() - at.getTime()) / DAY_MS);
    const stamp = value.replace('T', ' ').slice(0, 16);
    if (days <= 0) {
        return `${stamp} (오늘)`;
    }
    return `${stamp} (${days}일 전)`;
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

    fillTypeOptions();
    consoleTabs().forEach((tab) => {
        tab.addEventListener('click', () => showTab(tab.dataset.tab));
    });
    $('policy-reload').addEventListener('click', reloadPolicies);
    $('new-policy').addEventListener('click', () => openPolicyEditor(null));
    $('filter-type').addEventListener('change', renderPolicies);
    $('filter-verified').addEventListener('change', renderPolicies);
    $('policy-save').addEventListener('click', savePolicy);
    $('policy-delete').addEventListener('click', removePolicy);
    $('policy-cancel').addEventListener('click', () => $('policy-editor').close());
    $('policy-editor-close').addEventListener('click', () => $('policy-editor').close());
    $('policy-form').addEventListener('input', refreshPolicyPreview);

    // 기간을 바꾸면 서버를 다시 부른다 — 목록 필터와 달리 브라우저에 없는 데이터라 걸러낼 수 없다.
    $('external-reload').addEventListener('click', reloadExternals);
    $('filter-days').addEventListener('change', reloadExternals);

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
