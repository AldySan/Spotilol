package com.project.lol.webview.injections

object PlaylistSort {
    const val CONTENT = """
        (function(){
            if (window.splPlaylistSort) return;

            var LS_KEY = 'spotilol_playlist_sort';
            var RELOAD_KEY = 'spotilol_sort_reload';
            var MAX_LIMIT = 1000;
            var TTL = 30000;
            var STYLE_ID = 'spotilol-sort-style';

            var map = {};
            try {
                var raw = localStorage.getItem(LS_KEY);
                if (raw) {
                    var parsedStore = JSON.parse(raw);
                    if (parsedStore && !parsedStore.field) map = parsedStore;
                }
            } catch(e){ map = {}; }

            var state = null;
            var stateUri = null;
            var held = {};
            var sortedHits = {};
            var qcCache = null;
            var menuTimer = null;
            var menuObserver = null;

            var SORT_FIELDS = [
                ['TITLE_AND_ARTIST', '', 'Title'],
                ['ALBUM', 'ALBUM', 'Album'],
                ['ADDED_AT', 'ADDED_AT', 'Date added'],
                ['RELEASE_DATE', 'RELEASE_DATE', 'Release date'],
                ['DURATION', 'DURATION', 'Duration']
            ];

            function enabled(){ return window.__splPlaylistSortEnabled !== false; }
            function keyOf(st){ return st ? st.field + ':' + st.order : ''; }
            function sortKey(){ return keyOf(state); }
            function currentUri(){
                var m = location.pathname.match(/^\/playlist\/([A-Za-z0-9]+)/);
                if (m) return 'spotify:playlist:' + m[1];
                m = location.pathname.match(/^\/album\/([A-Za-z0-9]+)/);
                if (m) return 'spotify:album:' + m[1];
                return null;
            }
            function albumPage(){ return /^\/album\//.test(location.pathname); }
            function stateFor(uri){
                var s = uri ? map[uri] : null;
                if (s && (!s.field || (s.order !== 'ASC' && s.order !== 'DESC'))) return null;
                return s || null;
            }
            function syncState(){
                var uri = currentUri();
                if (uri === stateUri) return false;
                stateUri = uri;
                state = stateFor(uri);
                return true;
            }
            function save(){
                try { localStorage.setItem(LS_KEY, JSON.stringify(map)); } catch(e){}
            }

            function valueOf(item, field){
                if (albumPage()) {
                    var t = item && item.track;
                    if (!t) return null;
                    switch (field) {
                        case 'TITLE_AND_ARTIST': return t.name || null;
                        case 'DURATION': return (t.duration && t.duration.totalMilliseconds) || 0;
                    }
                    return null;
                }
                var d = item && item.itemV2 ? item.itemV2.data : null;
                if (!d) return null;
                switch (field) {
                    case 'TITLE_AND_ARTIST': return d.name || null;
                    case 'ALBUM': return (d.albumOfTrack && d.albumOfTrack.name) || null;
                    case 'DURATION': return (d.trackDuration && d.trackDuration.totalMilliseconds) || 0;
                    case 'RELEASE_DATE': return (d.albumOfTrack && d.albumOfTrack.date && d.albumOfTrack.date.isoString) || null;
                    case 'ADDED_AT': return (item.addedAt && item.addedAt.isoString) || null;
                }
                return null;
            }

            function sortItems(items, st){
                if (!st) return items;
                var field = st.field;
                var dir = st.order === 'DESC' ? -1 : 1;
                return items.slice().sort(function(a, b){
                    var x = valueOf(a, field), y = valueOf(b, field);
                    if (x === null && y === null) return 0;
                    if (x === null) return 1;
                    if (y === null) return -1;
                    if (typeof x === 'string' || typeof y === 'string') {
                        return String(x).localeCompare(String(y)) * dir;
                    }
                    if (x === y) return 0;
                    return (x < y ? -1 : 1) * dir;
                });
            }
            function readItems(j){
                try { return j.data.playlistV2.content.items || null; } catch(e){}
                try { return j.data.albumUnion.tracksV2.items || null; } catch(e){}
                return null;
            }
            function readTotal(j, fallback){
                try {
                    var t = j.data.playlistV2.content.totalCount;
                    if (typeof t === 'number') return t;
                } catch(e){}
                try {
                    var a = j.data.albumUnion.tracksV2.totalCount;
                    if (typeof a === 'number') return a;
                } catch(e){}
                return fallback;
            }
            function rebuild(j, items){
                var out = j;
                try {
                    if (j.data.playlistV2) {
                        var content = Object.assign({}, j.data.playlistV2.content, { items: items });
                        var pv2 = Object.assign({}, j.data.playlistV2, { content: content });
                        out = Object.assign({}, j, { data: Object.assign({}, j.data, { playlistV2: pv2 }) });
                    } else if (j.data.albumUnion) {
                        var tv2 = Object.assign({}, j.data.albumUnion.tracksV2, { items: items });
                        var au = Object.assign({}, j.data.albumUnion, { tracksV2: tv2 });
                        out = Object.assign({}, j, { data: Object.assign({}, j.data, { albumUnion: au }) });
                    }
                } catch(e){}
                var headers = null;
                try {
                    headers = new Headers();
                    headers.set('content-type', 'application/json');
                } catch(e){ headers = null; }
                return new Response(JSON.stringify(out), { status: 200, statusText: 'OK', headers: headers });
            }

            function findQueryClient(){
                if (qcCache) return qcCache;
                var el = document.querySelector('[data-testid="playlist-tracklist"]') || document.body;
                if (!el) return null;
                var keys = Object.keys(el), fkey = null;
                for (var i = 0; i < keys.length; i++) {
                    if (keys[i].indexOf('__reactFiber${'$'}') === 0) { fkey = keys[i]; break; }
                }
                if (!fkey) return null;
                var fiber = el[fkey];
                while (fiber && fiber.return) fiber = fiber.return;
                if (!fiber) return null;
                var stack = [fiber], seen = [], guard = 0;
                while (stack.length && guard++ < 60000) {
                    var node = stack.pop();
                    if (!node || seen.indexOf(node) !== -1) continue;
                    seen.push(node);
                    var props = node.memoizedProps;
                    if (props && props.value
                        && typeof props.value.getQueryData === 'function'
                        && typeof props.value.invalidateQueries === 'function') {
                        qcCache = props.value;
                        return qcCache;
                    }
                    if (node.child) stack.push(node.child);
                    if (node.sibling) stack.push(node.sibling);
                }
                return null;
            }

            function apply(){
                var uri = currentUri();
                if (!uri) return false;
                if (albumPage()) return false;
                var st = stateFor(uri);
                var wasSorted = !!held[uri] || !!st;
                held[uri] = null;
                if (!enabled() || !wasSorted) return false;
                if (st && !fieldSupported(st.field)) return false;
                var qc = findQueryClient();
                if (!qc || typeof qc.getQueryCache !== 'function') return false;
                var all = [];
                try { all = qc.getQueryCache().getAll() || []; } catch(e){ return false; }
                var hit = false;
                for (var i = 0; i < all.length; i++) {
                    var q = all[i];
                    if (!q || !q.queryKey) continue;
                    var str;
                    try { str = JSON.stringify(q.queryKey); } catch(e){ continue; }
                    if (str.indexOf(uri) === -1) continue;
                    try { qc.invalidateQueries({ queryKey: q.queryKey }); hit = true; } catch(e){}
                }
                return hit;
            }
            function injectStyle(){
                if (document.getElementById(STYLE_ID)) return;
                var s = document.createElement('style');
                s.id = STYLE_ID;
                s.textContent =
                    '[role="columnheader"][data-spl-sort]{-webkit-user-select:none;user-select:none}' +
                    '[role="columnheader"][data-spl-sort="ASC"]::after{content:"\\25B2";font-size:8px;margin-left:4px;opacity:.9}' +
                    '[role="columnheader"][data-spl-sort="DESC"]::after{content:"\\25BC";font-size:8px;margin-left:4px;opacity:.9}' +
                    '[data-spl-opt="head"]{border-top:1px solid rgba(255,255,255,.12)}' +
                    '.spl-sort-arrow{display:flex;align-items:center;justify-content:center;width:16px;height:16px;font-size:9px;line-height:1;color:var(--text-bright-accent,#1ed760)}';
                (document.head || document.documentElement).appendChild(s);
            }

            function fieldFor(label){
                var t = String(label || '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (!t) return null;
                if (t.indexOf('title') === 0) return 'TITLE_AND_ARTIST';
                if (t === 'album') return 'ALBUM';
                if (t.indexOf('duration') === 0) return 'DURATION';
                if (t.indexOf('release') === 0) return 'RELEASE_DATE';
                if (t.indexOf('date added') === 0) return 'ADDED_AT';
                return null;
            }

            function labelOf(cell){
                var label = cell.textContent || '';
                if (!label.trim()) {
                    var icon = cell.querySelector('[aria-label]');
                    if (icon) label = icon.getAttribute('aria-label') || '';
                }
                return label;
            }

            function decorate(){
                try {
                    syncState();
                    injectStyle();
                    var cells = document.querySelectorAll('[role="columnheader"]');
                    var uri = currentUri();
                    for (var i = 0; i < cells.length; i++) {
                        var cell = cells[i];
                        var field = uri ? fieldFor(labelOf(cell)) : null;
                        if (!enabled() || !field) {
                            if (cell.hasAttribute('data-spl-sort')) cell.removeAttribute('data-spl-sort');
                            continue;
                        }
                        var active = (state && state.field === field) ? state.order : '';
                        if (active) {
                            cell.setAttribute('data-spl-sort', active);
                            cell.setAttribute('aria-sort', active === 'ASC' ? 'ascending' : 'descending');
                        } else {
                            if (cell.hasAttribute('data-spl-sort')) cell.removeAttribute('data-spl-sort');
                            cell.setAttribute('aria-sort', 'none');
                        }
                    }
                    injectMenu();
                } catch(e){}
            }

            function columnsMenu(){
                var menu = document.getElementById('context-menu');
                if (!menu || !menu.children || !menu.children.length) return null;
                if (!menu.querySelector('button[role="menuitemcheckbox"][data-column]')) return null;
                return menu.querySelector('ul[role="menu"]');
            }
            function sectionTemplate(ul){
                for (var i = 0; i < ul.children.length; i++) {
                    var li = ul.children[i];
                    if (li.tagName === 'LI' && !li.querySelector('button')) return li.cloneNode(true);
                }
                return null;
            }
            function rowTemplate(ul){
                var b = ul.querySelector('button[role="menuitemcheckbox"][data-column]');
                return (b && b.closest('li')) ? b.closest('li').cloneNode(true) : null;
            }
            function arrowFor(li, active){
                var mark = li.querySelector('.spl-sort-arrow');
                if (!mark) return;
                mark.textContent = active ? (active === 'DESC' ? '\u25BC' : '\u25B2') : '';
            }
            function setActive(li, active){
                var btn = li.querySelector('button');
                if (!btn) return;
                btn.setAttribute('aria-checked', active ? 'true' : 'false');
                var sp = btn.querySelector('[data-encore-id="text"]');
                if (sp) {
                    if (active) sp.className = sp.className.replace('encore-internal-color-text-base', 'encore-internal-color-text-bright-accent');
                    else sp.className = sp.className.replace('encore-internal-color-text-bright-accent', 'encore-internal-color-text-base');
                }
                arrowFor(li, active);
            }
            function columnLabel(ul, col, fallback){
                if (!col) return fallback;
                var b = ul.querySelector('button[role="menuitemcheckbox"][data-column="' + col + '"]');
                var sp = b ? b.querySelector('span') : null;
                var t = sp ? String(sp.textContent || '').replace(/\s+/g, ' ').trim() : '';
                return t || fallback;
            }
            function headerLabel(field){
                var cells = document.querySelectorAll('[role="columnheader"]');
                for (var i = 0; i < cells.length; i++) {
                    if (fieldFor(labelOf(cells[i])) !== field) continue;
                    var t = String(labelOf(cells[i]) || '').replace(/\s+/g, ' ').trim();
                    if (t) return t;
                }
                return null;
            }
            function fieldSupported(field){
                if (albumPage()) return field === 'TITLE_AND_ARTIST' || field === 'DURATION';
                if (field !== 'ADDED_AT') return true;
                var ul = columnsMenu();
                if (ul && ul.querySelector('button[role="menuitemcheckbox"][data-column="' + field + '"]')) return true;
                return !!headerLabel(field);
            }
            function dropMenuRows(ul){
                var old = ul.querySelectorAll('[data-spl-opt]');
                for (var i = 0; i < old.length; i++) {
                    var li = old[i].closest ? old[i].closest('li') : null;
                    if (li && li.parentNode) li.parentNode.removeChild(li);
                    else if (old[i].parentNode) old[i].parentNode.removeChild(old[i]);
                }
            }

            function injectMenu(){
                try {
                    if (!enabled() || !currentUri()) return;
                    var ul = columnsMenu();
                    if (!ul) return;
                    var stamp = sortKey();
                    if (ul.querySelectorAll('[data-spl-opt]').length && ul.getAttribute('data-spl-menu') === stamp) return;
                    dropMenuRows(ul);
                    var row = rowTemplate(ul);
                    if (!row) return;
                    var head = sectionTemplate(ul);
                    if (head) {
                        var hs = head.querySelector('span');
                        if (hs) hs.textContent = 'Sort by';
                        head.setAttribute('data-spl-opt', 'head');
                        ul.appendChild(head);
                    }
                    for (var i = 0; i < SORT_FIELDS.length; i++) {
                        var field = SORT_FIELDS[i][0];
                        if (!fieldSupported(field)) continue;
                        var label = columnLabel(ul, SORT_FIELDS[i][1], headerLabel(field) || SORT_FIELDS[i][2]);
                        var active = (state && state.field === field) ? state.order : '';
                        var li = row.cloneNode(true);
                        var btn = li.querySelector('button');
                        if (!btn) continue;
                        btn.setAttribute('data-spl-opt', 'field');
                        btn.setAttribute('data-spl-field', field);
                        btn.setAttribute('role', 'menuitemradio');
                        btn.setAttribute('aria-label', label);
                        btn.removeAttribute('data-column');
                        var cb = btn.querySelector('[data-encore-id="formCheckbox"]');
                        if (cb && cb.parentNode) {
                            var mark = document.createElement('span');
                            mark.className = 'spl-sort-arrow';
                            mark.setAttribute('aria-hidden', 'true');
                            cb.parentNode.replaceChild(mark, cb);
                        }
                        var sp = btn.querySelector('[data-encore-id="text"]');
                        if (sp) sp.textContent = label;
                        if (active) btn.setAttribute('aria-label', label + (active === 'DESC' ? ', z to a' : ', a to z'));
                        setActive(li, active);
                        ul.appendChild(li);
                    }
                    ul.setAttribute('data-spl-menu', stamp);
                } catch(e){}
            }
            function scheduleMenu(){
                if (menuTimer) return;
                menuTimer = setTimeout(function(){ menuTimer = null; injectMenu(); }, 80);
            }
            function observeMenu(){
                try {
                    if (menuObserver || !window.MutationObserver) return;
                    var root = document.body || document.documentElement;
                    if (!root) return;
                    menuObserver = new MutationObserver(function(){
                        if (document.getElementById('context-menu')) scheduleMenu();
                    });
                    menuObserver.observe(root, { childList: true, subtree: true });
                } catch(e){}
            }

            function albumReload(){
                var key = sortKey() || 'off';
                var n = 0;
                try { n = parseInt(sessionStorage.getItem(RELOAD_KEY + ':' + key) || '0', 10) || 0; } catch(e){}
                if (n >= 2) return;
                try { sessionStorage.setItem(RELOAD_KEY + ':' + key, String(n + 1)); } catch(e){}
                setTimeout(function(){ try { location.reload(); } catch(e){} }, 200);
            }
            function afterSortChange(){
                decorate();
                if (!albumPage()) { apply(); return; }
                albumReload();
            }
            function setSort(field, order){
                var uri = currentUri();
                if (!field) { clearSort(); return; }
                if (!uri) return;
                map[uri] = { field: field, order: order === 'DESC' ? 'DESC' : 'ASC' };
                save();
                state = map[uri];
                stateUri = uri;
                afterSortChange();
            }
            function clearSort(){
                var uri = currentUri();
                if (uri && map[uri]) { delete map[uri]; save(); }
                state = null;
                stateUri = uri;
                afterSortChange();
            }
            function toggleField(field){
                if (!field) return;
                if (state && state.field === field && state.order === 'ASC') { setSort(field, 'DESC'); return; }
                if (state && state.field === field && state.order === 'DESC') { clearSort(); return; }
                setSort(field, 'ASC');
            }
            function refresh(){
                afterSortChange();
            }
            function tick(){
                var changed = syncState();
                decorate();
                if (changed && state && !albumPage()) apply();
            }

            window.splPlaylistSort = {
                get: function(){ return state ? { field: state.field, order: state.order } : null; },
                set: setSort,
                clear: clearSort,
                cycle: toggleField,
                refresh: refresh
            };

            var prevFetch = window.fetch.bind(window);
            window.fetch = function(input, init){
                try {
                    var url = typeof input === 'string' ? input : (input && input.url) || '';
                    if (url.indexOf('api-partner.spotify.com/pathfinder') === -1 || !init || !init.body) {
                        return prevFetch(input, init);
                    }
                    var body = init.body;
                    var parsed = typeof body === 'string' ? JSON.parse(body) : body;
                    var op = parsed && parsed.operationName;
                    var playlistOp = (op === 'fetchPlaylistContents' || op === 'fetchPlaylist');
                    if (!parsed || (op !== 'getAlbum' && !playlistOp)) {
                        return prevFetch(input, init);
                    }
                    var vars = parsed.variables || {};
                    var uri = vars.uri;
                    var want = playlistOp ? 'spotify:playlist:' : 'spotify:album:';
                    if (!uri || String(uri).indexOf(want) !== 0) return prevFetch(input, init);
                    var st = stateFor(uri);
                    if (!enabled() || !st || !fieldSupported(st.field)) return prevFetch(input, init);

                    var offset = vars.offset | 0;
                    var limit = (vars.limit | 0) || 50;
                    var key = keyOf(st);
                    var hit = held[uri];
                    if (!hit || hit.key !== key || (Date.now() - hit.ts) >= TTL) hit = null;

                    if (hit && (offset < hit.items.length || hit.total <= hit.items.length)) {
                        return Promise.resolve(rebuild(hit.envelope, hit.items.slice(offset, offset + limit)));
                    }

                    if (!hit) {
                        var req = Object.assign({}, parsed);
                        req.variables = Object.assign({}, vars, { offset: 0, limit: MAX_LIMIT });
                        var init2 = Object.assign({}, init);
                        init2.body = typeof body === 'string' ? JSON.stringify(req) : req;
                        return prevFetch(input, init2).then(function(resp){
                            return resp.clone().json().then(function(j){
                                var items = readItems(j);
                                if (!items || !items.length) return resp;
                                var sorted = sortItems(items, st);
                                held[uri] = {
                                    items: sorted,
                                    envelope: j,
                                    total: readTotal(j, sorted.length),
                                    key: key,
                                    ts: Date.now()
                                };
                                if (albumPage()) sortedHits[uri + '|' + key] = 1;
                                return rebuild(j, sorted.slice(offset, offset + limit));
                            }).catch(function(){ return resp; });
                        });
                    }

                    return prevFetch(input, init).then(function(resp){
                        return resp.clone().json().then(function(j){
                            var items = readItems(j);
                            if (!items || !items.length) return resp;
                            return rebuild(j, sortItems(items, st));
                        }).catch(function(){ return resp; });
                    });
                } catch(e) {
                    return prevFetch(input, init);
                }
            };

            document.addEventListener('click', function(ev){
                try {
                    var target = ev.target;
                    if (!target || !target.closest) return;
                    var opt = target.closest('[data-spl-opt="field"]');
                    if (!opt) return;
                    ev.preventDefault();
                    ev.stopPropagation();
                    toggleField(opt.getAttribute('data-spl-field'));
                } catch(e){}
            }, true);

            observeMenu();
            injectStyle();
            syncState();
            decorate();
            setInterval(tick, 1000);
            if (state) {
                if (albumPage()) {
                    setTimeout(function(){
                        if (sortedHits[currentUri() + '|' + sortKey()]) return;
                        albumReload();
                    }, 2500);
                } else {
                    setTimeout(apply, 1500);
                }
            }
        })();
    """
}
