package com.project.lol.webview.injections

object SpicyLyrics {
    const val CONTENT = """
        (function(){
            if (window.__spicyLyrics) return;
            window.__spicyLyrics = true;

            var cache = {};
            var pending = {};
            var settleCb = {};
            var seq = 0;

            function enabled(){ return window.__spicyLyricsEnabled !== false; }

            function idFromUri(uri){
                var m = /spotify:track:([A-Za-z0-9]{22})/.exec(uri || '');
                return m ? m[1] : null;
            }

            function trackMeta(){
                try {
                    var t = document.querySelector('[data-testid="context-item-info-title"]');
                    var a = document.querySelector('[data-testid="context-item-info-artist"]');
                    var d = document.querySelector('[data-testid="playback-duration"]');
                    var title = t ? (t.textContent || '').trim() : '';
                    var artist = a ? (a.textContent || '').trim() : '';
                    var dur = 0;
                    if (d) {
                        var parts = (d.textContent || '').trim().split(':');
                        if (parts.length === 2) dur = (parseInt(parts[0], 10) || 0) * 60 + (parseInt(parts[1], 10) || 0);
                    }
                    if (!title) {
                        var dt = (document.title || '').split(' \u2022 ');
                        if (dt.length >= 2) { title = dt[0].trim(); if (!artist) artist = dt[1].trim(); }
                    }
                    return { title: title, artist: artist, duration: dur };
                } catch(e){ return { title:'', artist:'', duration:0 }; }
            }

            function deliver(id, list, raw, title){
                delete pending[id];
                var payload = null;
                try { if (raw && raw !== 'null') payload = JSON.parse(raw); } catch(e){ payload = null; }
                var unsynced = payload && payload.lyrics && payload.lyrics.syncType === 'UNSYNCED';
                if (!(unsynced && !title)) cache[id] = payload;
                for (var i = 0; i < list.length; i++) { try { list[i](cache[id]); } catch(e){} }
            }

            window.__splSpicySettle = function(reqId, raw){
                var slot = settleCb[reqId];
                if (!slot) return;
                delete settleCb[reqId];
                deliver(slot.id, slot.list, raw, slot.title);
            };

            function resolveAsync(id, cb){
                if (!enabled() || !id || id.length !== 22) { cb(null); return; }
                if (Object.prototype.hasOwnProperty.call(cache, id)) { cb(cache[id]); return; }
                var list = pending[id];
                if (list) { list.push(cb); return; }
                list = pending[id] = [cb];
                var meta = trackMeta();
                if (typeof AndBridge.spicyLyricsAsync === 'function') {
                    var reqId = ++seq;
                    settleCb[reqId] = { id: id, list: list, title: meta.title };
                    setTimeout(function(){
                        if (settleCb[reqId]) {
                            delete settleCb[reqId];
                            deliver(id, list, 'null', meta.title);
                        }
                    }, 45000);
                    try {
                        AndBridge.spicyLyricsAsync(id, meta.title, meta.artist, meta.duration, reqId);
                    } catch(err) {
                        delete settleCb[reqId];
                        deliver(id, list, 'null', meta.title);
                    }
                } else {
                    var raw = null;
                    try { raw = AndBridge.spicyLyrics(id, meta.title, meta.artist, meta.duration); } catch(e){}
                    deliver(id, list, raw, meta.title);
                }
            }

            function jsonResponse(obj){
                var h = new Headers();
                h.set('content-type', 'application/json');
                return new Response(JSON.stringify(obj), { status: 200, statusText: 'OK', headers: h });
            }

            var prev = window.fetch.bind(window);

            window.fetch = function(input, init){
                if (!enabled()) return prev(input, init);
                var url = typeof input === 'string' ? input : (input && input.url) || '';
                var isMeta = url.indexOf('metadata/4/track/') !== -1;
                var lyr = /color-lyrics\/v2\/track\/([A-Za-z0-9]{22})/.exec(url);
                if (!isMeta && !lyr) return prev(input, init);
                return prev(input, init).then(function(resp){
                    if (isMeta) {
                        if (resp.status !== 200) return resp;
                        return resp.clone().json().then(function(json){
                            if (!json || json.has_lyrics !== undefined) return resp;
                            var id = idFromUri(json.canonical_uri);
                            if (!id) return resp;
                            if (Object.prototype.hasOwnProperty.call(cache, id)) {
                                if (!cache[id]) return resp;
                                json.has_lyrics = true;
                                return jsonResponse(json);
                            }
                            resolveAsync(id, function(payload){
                                if (payload) nudgeAvailability();
                            });
                            return resp;
                        }).catch(function(){ return resp; });
                    }
                    if (resp.status !== 404) return resp;
                    var id = lyr[1];
                    if (Object.prototype.hasOwnProperty.call(cache, id)) {
                        return cache[id] ? jsonResponse(cache[id]) : resp;
                    }
                    return new Promise(function(done){
                        resolveAsync(id, function(payload){
                            done(payload ? jsonResponse(payload) : resp);
                        });
                    });
                });
            };

            function nudgeAvailability(){
                try {
                    var btn = document.querySelector('button[data-testid="lyrics-button"]');
                    if (!btn) return false;
                    var keys = Object.keys(btn), fk = null;
                    for (var i = 0; i < keys.length; i++) {
                        if (keys[i].indexOf('__reactFiber$') === 0) { fk = keys[i]; break; }
                    }
                    if (!fk) return false;
                    var node = btn[fk], guard = 0, obs = null;
                    while (node && guard++ < 16) {
                        var hook = node.memoizedState, hg = 0, states = [];
                        while (hook && hg++ < 14) { states.push(hook.memoizedState); hook = hook.next; }
                        for (var j = 0; j < states.length; j++) {
                            var v = states[j];
                            if (v && v.options && v.options.queryKey &&
                                JSON.stringify(v.options.queryKey).indexOf('useLyricsAvailability') !== -1) obs = v;
                        }
                        if (obs) break;
                        node = node.return;
                    }
                    if (!obs || typeof obs.refetch !== 'function') return false;
                    obs.refetch();
                    return true;
                } catch(e) { return false; }
            }

            function armTrackWatch(){
                if (typeof window.splOnTrackChange !== 'function') return;
                window.splOnTrackChange(function(uri, id){
                    if (!enabled() || !id) return;
                    if (Object.prototype.hasOwnProperty.call(cache, id)) return;
                    resolveAsync(id, function(payload){
                        if (!payload) return;
                        var tries = 0;
                        var iv = setInterval(function(){
                            tries++;
                            var b = document.querySelector('button[data-testid="lyrics-button"]');
                            if (b && !b.disabled) { clearInterval(iv); return; }
                            nudgeAvailability();
                            if (tries >= 8) clearInterval(iv);
                        }, 2500);
                    });
                });
            }

            window.spicyLyrics = {
                refresh: function(){ cache = {}; },
                has: function(id){ return !!(id && cache[id]); },
                nudge: nudgeAvailability
            };

            armTrackWatch();
        })();
    """
}