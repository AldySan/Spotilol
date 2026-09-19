package com.project.lol.webview.injections

/**
 * sort the rows of a playlist from the columns dropdown.
 *
 * spotify already ships a full sort engine but never wires it up for playlist
 * tracklists, so the headers are dead divs and the api ignores the sort variable
 * too. what does work is sorting the items as they come back from pathfinder,
 * then poking the query cache so the page refetches and the new order actually
 * lands on screen.
 *
 * the request is rewritten to pull the whole playlist in one go so the order is
 * global and not per page. the sorted list is kept in memory so the following
 * pages cost no extra round trip. huge playlists just get sorted per page.
 *
 * the sort lives in a sort by section added to the columns dropdown, which is
 * where people already look for column stuff. the rows are clones of spotify own
 * column rows, but the tick is swapped for a direction arrow, since a tick down
 * there just reads as one more column toggle and the section goes unnoticed.
 * tapping a row goes a to z, then z to a, then back to the original order, and
 * the arrow in the menu plus the one on the header show the direction at a glance.
 */
object PlaylistSort {
    const val CONTENT = """
        (function(){
            if (window.splPlaylistSort) return;

            var LS_KEY = 'spotilol_playlist_sort';
            var MAX_LIMIT = 1000;
            var TTL = 30000;
            var STYLE_ID = 'spotilol-sort-style';

            var state = null;
            try {
                var raw = localStorage.getItem(LS_KEY);
                if (raw) state = JSON.parse(raw);
            } catch(e){ state = null; }
            if (state && (!state.field || (state.order !== 'ASC' && state.order !== 'DESC'))) state = null;

            var held = {};
            var qcCache = null;
            var menuTimer = null;
            var menuObserver = null;

            // label shown in the dropdown, the column name comes from spotify own rows
            var SORT_FIELDS = [
                ['TITLE_AND_ARTIST', '', 'Title'],
                ['ALBUM', 'ALBUM', 'Album'],
                ['RELEASE_DATE', 'RELEASE_DATE', 'Release date'],
                ['DURATION', 'DURATION', 'Duration']
            ];

            function enabled(){ return window.__splPlaylistSortEnabled !== false; }
            function sortKey(){ return state ? state.field + ':' + state.order : ''; }
            function currentUri(){
                var m = location.pathname.match(/^\/playlist\/([A-Za-z0-9]+)/);
                return m ? 'spotify:playlist:' + m[1] : null;
            }
            function save(){
                try {
                    if (state) localStorage.setItem(LS_KEY, JSON.stringify(state));
                    else localStorage.removeItem(LS_KEY);
                } catch(e){}
            }

            // what we sort by, straight from the pathfinder items
            function valueOf(item, field){
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

            function sortItems(items){
                if (!state) return items;
                var field = state.field;
                var dir = state.order === 'DESC' ? -1 : 1;
                return items.slice().sort(function(a, b){
                    var x = valueOf(a, field), y = valueOf(b, field);
                    // rows with no value for this column go last
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
            // helpers to read and rebuild the response
            function readItems(j){
                try { return j.data.playlistV2.content.items || null; } catch(e){ return null; }
            }
            function readTotal(j, fallback){
                try {
                    var t = j.data.playlistV2.content.totalCount;
                    return (typeof t === 'number') ? t : fallback;
                } catch(e){ return fallback; }
            }
            function rebuild(j, items){
                var content = Object.assign({}, j.data.playlistV2.content, { items: items });
                var pv2 = Object.assign({}, j.data.playlistV2, { content: content });
                var data = Object.assign({}, j.data, { playlistV2: pv2 });
                var out = Object.assign({}, j, { data: data });
                var headers = null;
                try {
                    headers = new Headers();
                    headers.set('content-type', 'application/json');
                } catch(e){ headers = null; }
                return new Response(JSON.stringify(out), { status: 200, statusText: 'OK', headers: headers });
            }

            // react query access, only used to trigger a refetch
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

            // refetch the current playlist so the sort actually lands. a playlist
            // that is already cached is never fetched again by browsing around, so
            // we poke its queries ourselves.
            function apply(){
                var uri = currentUri();
                if (!uri) return false;
                // a playlist we already fed sorted rows has to be refetched even when the
                // sort just got turned off, otherwise the list keeps the order from the
                // cache and it looks sorted while the menu says the sort is off
                var wasSorted = !!held[uri] || !!state;
                held[uri] = null;
                if (!enabled() || !wasSorted) return false;
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
            // column headers, which ones sort and the little arrow
            function injectStyle(){
                if (document.getElementById(STYLE_ID)) return;
                var s = document.createElement('style');
                s.id = STYLE_ID;
                s.textContent =
                    '[role="columnheader"][data-spl-sort]{-webkit-user-select:none;user-select:none}' +
                    '[role="columnheader"][data-spl-sort="ASC"]::after{content:"\\25B2";font-size:8px;margin-left:4px;opacity:.9}' +
                    '[role="columnheader"][data-spl-sort="DESC"]::after{content:"\\25BC";font-size:8px;margin-left:4px;opacity:.9}' +
                    // the sort section has to read as its own group, spotify gives the
                    // heading the same look as any other section so we add the rule
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

            // the columns dropdown, only ours to touch when it really is the columns
            // one. keyed off data column so a locale change cannot break the match
            function columnsMenu(){
                var menu = document.getElementById('context-menu');
                if (!menu || !menu.children || !menu.children.length) return null;
                if (!menu.querySelector('button[role="menuitemcheckbox"][data-column]')) return null;
                return menu.querySelector('ul[role="menu"]');
            }
            // the section heading in the menu has no button inside it
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
            // the tick spotify draws is what makes a row read as a column toggle, so
            // sort rows swap it for a direction arrow. the slot is kept so rows align
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
            // reuse spotify own wording so the row matches the rest of the menu
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
                        // the native tick would read as one more column toggle, so it
                        // gets swapped for a direction arrow in the same slot
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
            // the menu is built by react on every open, so watch for it to show up.
            // the check is one getElementById so a busy page costs nothing
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

            // public api
            function setSort(field, order){
                if (!field) { clearSort(); return; }
                state = { field: field, order: order === 'DESC' ? 'DESC' : 'ASC' };
                save();
                decorate();
                apply();
            }
            function clearSort(){
                state = null;
                save();
                decorate();
                apply();
            }
            function toggleField(field){
                if (!field) return;
                if (state && state.field === field && state.order === 'ASC') { setSort(field, 'DESC'); return; }
                if (state && state.field === field && state.order === 'DESC') { clearSort(); return; }
                setSort(field, 'ASC');
            }
            function refresh(){
                decorate();
                apply();
            }

            window.splPlaylistSort = {
                get: function(){ return state ? { field: state.field, order: state.order } : null; },
                set: setSort,
                clear: clearSort,
                cycle: toggleField,
                refresh: refresh
            };

            // fetch interceptor
            var prevFetch = window.fetch.bind(window);
            window.fetch = function(input, init){
                try {
                    var url = typeof input === 'string' ? input : (input && input.url) || '';
                    if (!enabled() || !state || url.indexOf('api-partner.spotify.com/pathfinder') === -1
                        || !init || !init.body) {
                        return prevFetch(input, init);
                    }
                    var body = init.body;
                    var parsed = typeof body === 'string' ? JSON.parse(body) : body;
                    if (!parsed || (parsed.operationName !== 'fetchPlaylistContents'
                        && parsed.operationName !== 'fetchPlaylist')) {
                        return prevFetch(input, init);
                    }
                    var vars = parsed.variables || {};
                    var uri = vars.uri;
                    if (!uri || String(uri).indexOf('spotify:playlist:') !== 0) return prevFetch(input, init);

                    var offset = vars.offset | 0;
                    var limit = (vars.limit | 0) || 50;
                    var key = sortKey();
                    var hit = held[uri];
                    if (!hit || hit.key !== key || (Date.now() - hit.ts) >= TTL) hit = null;

                    // a page we already sorted, serve it from memory with no round trip.
                    // holding the whole playlist means a page past its end is just empty
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
                                var sorted = sortItems(items);
                                held[uri] = {
                                    items: sorted,
                                    envelope: j,
                                    total: readTotal(j, sorted.length),
                                    key: key,
                                    ts: Date.now()
                                };
                                return rebuild(j, sorted.slice(offset, offset + limit));
                            }).catch(function(){ return resp; });
                        });
                    }

                    // a range past the list we hold, so the playlist is bigger than our
                    // max limit. fetch that page and sort it on its own
                    return prevFetch(input, init).then(function(resp){
                        return resp.clone().json().then(function(j){
                            var items = readItems(j);
                            if (!items || !items.length) return resp;
                            return rebuild(j, sortItems(items));
                        }).catch(function(){ return resp; });
                    });
                } catch(e) {
                    return prevFetch(input, init);
                }
            };

            // picking a row from the sort by section.
            // the menu is left open on purpose so the tick and the direction update
            // in front of you, then a tap outside closes it like any other menu
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
            decorate();
            setInterval(decorate, 1000);
            // apply once the player has mounted so a saved choice survives reloads and
            // also covers playlists served straight from the query cache
            if (state) setTimeout(apply, 1500);
        })();
    """
}
