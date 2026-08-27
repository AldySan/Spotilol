package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Playlist Sort (Experimental).
 * GitHub: https://github.com/AldySan
 */

object PlaylistSort {
    const val CONTENT = """
        (function(){
            if (window._splPlaylistSortInit) return;
            window._splPlaylistSortInit = true;

            function splToast(msg) {
                try { AndBridge.deferMessage(msg); } catch (e) {}
            }

            function splSleep(ms) {
                return new Promise(function(res){ setTimeout(res, ms); });
            }

            function splApiRaw(method, url, body) {
                var headers = { 'Authorization': window.spotAuthToken };
                var opts = { method: method, headers: headers };
                if (body) {
                    opts.body = JSON.stringify(body);
                    headers['Content-Type'] = 'application/json';
                }
                var raw = AndBridge.nFetch(url, JSON.stringify(opts));
                var r;
                try { r = JSON.parse(raw); }
                catch (e) {
                    throw new Error('Bridge error: nFetch returned non-JSON for ' + method + ' ' + url + ' → ' + String(raw).slice(0, 120));
                }
                if (!r || r.status === 0) {
                    throw new Error('Network error (status 0) on ' + method + ' ' + url);
                }
                return r;
            }

            function splRetryAfter(r) {
                var hk = Object.keys(r.headers || {});
                for (var i = 0; i < hk.length; i++) {
                    if (/retry-after/i.test(hk[i])) {
                        var v = parseInt(r.headers[hk[i]], 10);
                        if (v > 0) return v;
                    }
                }
                return 0;
            }

            function splApiErr(prefix, r) {
                var msg = '';
                try {
                    var d = JSON.parse(r.body || '{}');
                    if (d && d.error) msg = d.error.message || (typeof d.error === 'string' ? d.error : '');
                } catch (e) {}
                var s = prefix + ' (HTTP ' + r.status + ')';
                if (msg) s += ' - ' + msg;
                return s;
            }

            var SPL_TRIES = 4;

            async function splApi(method, url, body, onStatus) {
                onStatus = onStatus || function(){};
                var r = null, lastWait = 0;
                for (var t = 1; t <= SPL_TRIES; t++) {
                    r = splApiRaw(method, url, body);
                    if (r.status !== 429) return r;
                    lastWait = splRetryAfter(r) || (t + 2);
                    if (t < SPL_TRIES) {
                        var s = 'Rate limited (429) - retry ' + t + '/' + (SPL_TRIES - 1) + ' in ' + lastWait + 's';
                        onStatus(s);
                        splToast(s);
                        await splSleep(lastWait * 1000);
                    }
                }
                throw new Error('Rate limited (HTTP 429) - still throttled after ' + (SPL_TRIES - 1) +
                    ' retries (last backoff ' + lastWait + 's). Wait a minute, then sort again.');
            }

            window.splSortPlaylist = async function(direction, onStatus) {
                onStatus = onStatus || function(){};
                if (window._splSorting) throw new Error('A sort is already running - wait for it to finish');
                if (!window.spotAuthToken) throw new Error('No auth token yet - play a track first, then sort');
                var m = location.pathname.match(/\/playlist\/([A-Za-z0-9]+)/);
                if (!m) throw new Error('Not on a playlist page (no /playlist/<id> in the URL)');
                var plId = m[1];

                window._splSorting = true;
                try {
                    onStatus('Fetching tracks…');
                    var items = [], offset = 0, total = 1, snapshotId = null;
                    while (offset < total) {
                        var url = 'https://api.spotify.com/v1/playlists/' + plId +
                            '/items?fields=total,snapshot_id,items(added_at,track(uri,name))' +
                            '&limit=100&offset=' + offset;
                        var r = await splApi('GET', url, null, onStatus);
                        if (r.status === 403) throw new Error("Can't sort (HTTP 403) - you don't own this playlist");
                        if (r.status === 401) throw new Error('Token expired (HTTP 401) - play a track to refresh it, then retry');
                        if (r.status !== 200) throw new Error(splApiErr('Fetching tracks failed', r));
                        var d;
                        try { d = JSON.parse(r.body || '{}'); }
                        catch (e) { throw new Error('Bad JSON from API at offset ' + offset + ': ' + String(r.body).slice(0, 120)); }
                        total = d.total || 0;
                        snapshotId = d.snapshot_id || snapshotId;
                        (d.items || []).forEach(function(it) { items.push(it); });
                        offset += 100;
                        onStatus('Fetching tracks… ' + items.length + '/' + total);
                        if (!total) break;
                    }
                    if (items.length < 2) return 'Nothing to sort - playlist has fewer than 2 tracks';

                    var sorted = items.slice().sort(function(a, b) {
                        var x = a.added_at || '', y = b.added_at || '';
                        return direction === 'desc' ? (x < y ? 1 : x > y ? -1 : 0)
                                                     : (x < y ? -1 : x > y ? 1 : 0);
                    });

                    var plan = [], sim = items.slice();
                    for (var i = 0; i < sorted.length; i++) {
                        var j = -1;
                        for (var k = i; k < sim.length; k++) {
                            if (sim[k] === sorted[i]) { j = k; break; }
                        }
                        if (j < 0 || j === i) continue;
                        sim.splice(i, 0, sim.splice(j, 1)[0]);
                        plan.push({ from: j, to: i });
                    }
                    if (plan.length === 0) return 'Already sorted ✓ (' + items.length + ' tracks, 0 moves needed)';

                    var putUrl = 'https://api.spotify.com/v1/playlists/' + plId + '/items';
                    for (var p = 0; p < plan.length; p++) {
                        onStatus('Reordering… move ' + (p + 1) + '/' + plan.length);
                        var body = { range_start: plan[p].from, insert_before: plan[p].to, range_length: 1 };
                        if (snapshotId) body.snapshot_id = snapshotId;
                        var pr = await splApi('PUT', putUrl, body, onStatus);
                        if (pr.status === 403) throw new Error("Reorder rejected (HTTP 403) - you don't own this playlist. Applied " + p + '/' + plan.length + ' moves; playlist is PARTIALLY sorted - run the sort again');
                        if (pr.status === 401) throw new Error('Token expired (HTTP 401) mid-sort. Applied ' + p + '/' + plan.length + ' moves; playlist is PARTIALLY sorted - play a track, then sort again');
                        if (pr.status !== 201) throw new Error(splApiErr('Reorder failed at move ' + (p + 1) + '/' + plan.length + ' (applied ' + p + '; playlist is PARTIALLY sorted - run the sort again)', pr));
                        try { snapshotId = JSON.parse(pr.body || '{}').snapshot_id || snapshotId; } catch (e) {}

                        await splSleep(100);
                    }

                    var done = 'Sorted ' + (direction === 'desc' ? 'newest first' : 'oldest first') +
                        ' - ' + plan.length + ' moves ✓';

                    setTimeout(function() {
                        var first = document.querySelector('[data-testid="tracklist-row"] a[href*="/track/"]');
                        var expect = sorted[0] && sorted[0].track && sorted[0].track.name;
                        if (first && expect && first.textContent.trim() !== expect) {
                            history.pushState({}, '', '/playlist/' + plId);
                            window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                        }
                    }, 1500);

                    return done;
                } finally {
                    window._splSorting = false;
                }
            };

            var splDir = 'asc';
            function injectPill() {
                var ctx = document.querySelector('section[data-testid="playlist-page"]');
                if (!ctx || ctx.querySelector('#spl-sort-fallback')) return;
                if (!document.querySelector('[data-testid="edit-image-button"]')) return;
                var actionBar = ctx.querySelector('[data-testid="action-bar-row"]');
                if (!actionBar) return;

                var fb = document.createElement('button');
                fb.id = 'spl-sort-fallback';
                fb.setAttribute('type', 'button');
                fb.setAttribute('aria-label', 'Sort by Date Added');
                fb.innerHTML =
                    '<svg viewBox="0 0 16 16" width="14" height="14" style="margin-right:6px;flex-shrink:0">' +
                    '<path fill="currentColor" d="M2 3h12v1.5H2V3zm2 4h8v1.5H4V7zm2 4h4v1.5H6V11z"/>' +
                    '</svg><span>Sort: Oldest first</span>';
                fb.style.cssText =
                    'display:inline-flex;align-items:center;justify-content:center;' +
                    'min-height:40px;min-width:44px;padding:8px 14px;' +
                    'border-radius:9999px;border:1px solid rgba(255,255,255,.3);' +
                    'background:rgba(255,255,255,.08);color:#fff;cursor:pointer;' +
                    'font-size:13px;font-weight:700;font-family:inherit;margin-left:8px;white-space:nowrap';

                var label = fb.querySelector('span');
                fb.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (fb.disabled) return;
                    fb.disabled = true;
                    fb.title = '';
                    var ok = false;
                    window.splSortPlaylist(splDir, function(s) {
                        label.textContent = s;
                        fb.title = s;
                    }).then(function(msg) {
                        ok = true;
                        splToast(msg);
                        label.textContent = '✓ Sorted';
                        fb.title = msg;
                    }).catch(function(err) {
                        var m = String(err && err.message || err);
                        splToast(m);
                        label.textContent = '✕ Failed';
                        fb.title = m;
                    }).finally(function() {
                        setTimeout(function() {
                            fb.disabled = false;
                            if (ok) splDir = splDir === 'asc' ? 'desc' : 'asc';
                            label.textContent = splDir === 'asc' ? 'Sort: Oldest first' : 'Sort: Newest first';
                        }, ok ? 1500 : 4000);
                    });
                });
                actionBar.appendChild(fb);
            }

            setInterval(injectPill, 2000);
            injectPill();
        })();
    """
}