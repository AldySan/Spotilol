package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Custom Search Overlay.
 *
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⡀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣾⠙⠻⢶⣄⡀⠀⠀⠀⢀⣤⠶⠛⠛⡇⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢹⣇⠀⠀⣙⣿⣦⣤⣴⣿⣁⠀⠀⣸⠇⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⣡⣾⣿⣿⣿⣿⣿⣿⣿⣷⣌⠋⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣴⣿⣷⣄⡈⢻⣿⡟⢁⣠⣾⣿⣦⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢹⣿⣿⣿⣿⠘⣿⠃⣿⣿⣿⣿⡏⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⠀⠈⠛⣰⠿⣆⠛⠁⠀⡀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣼⣿⣦⠀⠘⠛⠋⠀⣴⣿⠁⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⣤⣶⣾⣿⣿⣿⣿⡇⠀⠀⠀⢸⣿⣏⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⣠⣶⣿⣿⣿⣿⣿⣿⣿⣿⠿⠿⠀⠀⠀⠾⢿⣿⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⣠⣿⣿⣿⣿⣿⣿⡿⠟⠋⣁⣠⣤⣤⡶⠶⠶⣤⣄⠈⠀⠀⠀⠀⠀⠀
⠀⠀⠀⢰⣿⣿⣮⣉⣉⣉⣤⣴⣶⣿⣿⣋⡥⠄⠀⠀⠀⠀⠉⢻⣄⠀⠀⠀⠀⠀
⠀⠀⠀⠸⣿⣿⣿⣿⣿⣿⣿⣿⣿⣟⣋⣁⣤⣀⣀⣤⣤⣤⣤⣄⣿⡄⠀⠀⠀⠀
⠀⠀⠀⠀⠙⠿⣿⣿⣿⣿⣿⣿⣿⡿⠿⠛⠋⠉⠁⠀⠀⠀⠀⠈⠛⠃⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠉⠉⠉⠉⠉⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
 */

object SearchOverlay {
    const val CONTENT = """
            (function(){
                if(window.splSearchInit) return;
                window.splSearchInit = true;
            
                var panel = null, pInput = null, pList = null, debTimer = null, seq = 0;
                var lastQ = '';
            
                var libCache = {};
                var libQueue = [];
            
                var HASH = '23f33ca50a0f4153dafc5cd1b4d1370db01b72130c2994bd0ffd07d5a7fee8f0';
                var RECENT_HASH = '3ec071f88e403779d4da9bc5744feb9d64cd07d10daf1f966b912baadaa3d598';
                var LIB_CHECK_HASH = '134337999233cc6fdd6b1e6dbf94841409f04a946c5c7b744b09ba0dfe5a85ed';
                var LIB_TOGGLE_HASH = '1ad0d40b3c09660d818b9e770eb1e84745dfbe941df159a64f8772b6fa2bfc3a';
            
                var ICONS = {
                    search: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>',
                    artist: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-3.31 0-6 1.79-6 4v2h12v-2c0-2.21-2.69-4-6-4z"/></svg>',
                    track: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 3v9.28a4.39 4.39 0 0 0-1.5-.26c-2.49 0-4.5 1.79-4.5 4s2.01 4 4.5 4 4.5-1.79 4.5-4V7h4V3h-7z"/></svg>',
                    album: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 12m-3.5 0a3.5 3.5 0 1 0 7 0 3.5 3.5 0 1 0-7 0zM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 17a7 7 0 1 1 0-14 7 7 0 0 1 0 14z"/></svg>',
                    playlist: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M14.5 4.5v11.1a3.5 3.5 0 1 0 1 2.4V8.5h4V4.5h-5zM4 5h9v1.5H4V5zm0 4h9v1.5H4V9zm0 4h5v1.5H4v-1.5z"/></svg>',
                    podcast: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2z"/></svg>',
                    clear: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M3.293 3.293a1 1 0 0 1 1.414 0L12 10.586l7.293-7.293a1 1 0 1 1 1.414 1.414L13.414 12l7.293 7.293a1 1 0 0 1-1.414 1.414L12 13.414l-7.293 7.293a1 1 0 0 1-1.414-1.414L10.586 12 3.293 4.707a1 1 0 0 1 0-1.414"/></svg>',
                    browse: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M15 15.5c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2"/><path fill="currentColor" d="M1.513 9.37A1 1 0 0 1 2.291 9h19.418a1 1 0 0 1 .979 1.208l-2.339 11a1 1 0 0 1-.978.792H4.63a1 1 0 0 1-.978-.792l-2.339-11a1 1 0 0 1 .201-.837zM3.525 11l1.913 9h13.123l1.913-9H3.525zM4 2a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v4h-2V3H6v3H4z"/></svg>',
                    add: '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M8 1.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8"/><path fill="currentColor" d="M11.75 8a.75.75 0 0 1-.75.75H8.75V11a.75.75 0 0 1-1.5 0V8.75H5a.75.75 0 0 1 0-1.5h2.25V5a.75.75 0 0 1 1.5 0v2.25H11a.75.75 0 0 1 .75.75"/></svg>',
                    added: '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8m11.748-1.97a.75.75 0 0 0-1.06-1.06l-4.47 4.47-1.405-1.406a.75.75 0 1 0-1.061 1.06l2.466 2.467 5.53-5.53z"/></svg>',
                    play: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="m7.05 3.606 13.49 7.788a.7.7 0 0 1 0 1.212L7.05 20.394A.7.7 0 0 1 6 19.788V4.212a.7.7 0 0 1 1.05-.606"/></svg>'
                };
            
                function iconFor(kind){ return ICONS[kind] || ICONS.search; }
            
                function stripId(uri, prefix){ return (uri || '').replace(prefix, ''); }
            
                function pickImg(sources){
                    if(!sources || !sources.length) return '';
                    var best = '', bestW = Infinity, any = '', anyW = -1;
                    for(var i = 0; i < sources.length; i++){
                        var s = sources[i];
                        if(!s || !s.url) continue;
                        var w = s.width || 0;
                        if(w > anyW){ any = s.url; anyW = w; }
                        if(w >= 40 && w < bestW){ best = s.url; bestW = w; }
                    }
                    return best || any;
                }
            
                function vw(){
                    try {
                        if(window.visualViewport && window.visualViewport.width) return window.visualViewport.width;
                    } catch(e){}
                    return window.innerWidth || 411;
                }
            
                function css(){
                    var st = document.createElement('style');
                    st.textContent = [
                        '#global-nav-bar input[data-testid="search-input"]{display:none!important}',
                        '#global-nav-bar form[role="search"]{min-width:48px!important;width:48px!important;max-width:48px!important;height:48px!important;min-height:48px!important;display:flex!important;align-items:center!important}',
                        '#global-nav-bar form[role="search"] [class*="form-input-icon__icon"]{position:static!important;top:auto!important;transform:none!important}',
                        '#global-nav-bar form[role="search"] > :not([class*="form-input-icon__icon--leading"]){display:none!important}',
                        'html.spl-search-active [data-testid="search-dropdown"],html.spl-search-active [data-testid="search-page-searchbar-searchbar-dropdown"]{display:none!important}',
                        '#splSearchPanel{position:fixed;z-index:2147483647;background:rgba(24,24,24,.98);border:1px solid rgba(255,255,255,.08);border-radius:14px;box-shadow:0 8px 32px rgba(0,0,0,.6);display:none;flex-direction:column;overflow:hidden;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;color:#fff}',
                        '#splSearchPanel .spl-sph{display:flex;align-items:center;gap:10px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,.06)}',
                        '#splSearchPanel .spl-spi{flex:1;min-width:0;background:transparent;border:none;outline:none;color:#fff;font-size:14px;height:32px}',
                        '#splSearchPanel .spl-spi::placeholder{color:rgba(255,255,255,.4)}',
                        '#splSearchPanel .spl-spx,#splSearchPanel .spl-sbr{box-sizing:border-box;width:30px;height:30px;flex-shrink:0;background:none;border:none;border-radius:50%;color:rgba(255,255,255,.7);cursor:pointer;display:flex;align-items:center;justify-content:center;padding:0;transition:background .15s}',
                        '#splSearchPanel .spl-spx:hover,#splSearchPanel .spl-sbr:hover{background:rgba(255,255,255,.12);color:#fff}',
                        '#splSearchPanel .spl-spx svg,#splSearchPanel .spl-sbr svg{width:16px;height:16px}',
                        '#splSearchPanel .spl-sfav{box-sizing:border-box;width:28px;height:28px;flex-shrink:0;background:none;border:none;border-radius:50%;color:rgba(255,255,255,.6);cursor:pointer;display:flex;align-items:center;justify-content:center;padding:0;transition:background .15s,color .15s}',
                        '#splSearchPanel .spl-sfav:hover{background:rgba(255,255,255,.12);color:#fff}',
                        '#splSearchPanel .spl-sfav.saved{color:var(--spl-accent,#1DB954)}',
                        '#splSearchPanel .spl-sfav svg{width:16px;height:16px}',
                        '#splSearchPanel .spl-slist{max-height:55vh;overflow-y:auto;padding:6px;overscroll-behavior:contain}',
                        '#splSearchPanel .spl-srow{display:flex;align-items:center;gap:10px;padding:8px;border-radius:8px;cursor:pointer;min-height:44px;box-sizing:border-box}',
                        '#splSearchPanel .spl-srow:hover{background:rgba(255,255,255,.08)}',
                        '#splSearchPanel .spl-sicon{position:relative;width:36px;height:36px;flex-shrink:0;border-radius:6px;overflow:hidden;background:#282828;display:flex;align-items:center;justify-content:center;color:#b3b3b3}',
                        '#splSearchPanel .spl-sicon img{width:100%;height:100%;object-fit:cover}',
                        '#splSearchPanel .spl-sicon svg{width:18px;height:18px}',
                        '#splSearchPanel .spl-splay{position:absolute;inset:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;color:#fff;opacity:0;transition:opacity .15s;cursor:pointer}',
                        '#splSearchPanel .spl-srow:hover .spl-splay{opacity:1}',
                        '#splSearchPanel .spl-splay svg{width:18px;height:18px}',
                        '#splSearchPanel .spl-stx{flex:1;min-width:0;overflow:hidden}',
                        '#splSearchPanel .spl-sname{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.3}',
                        '#splSearchPanel .spl-sname a,#splSearchPanel .spl-ssub a{color:inherit;text-decoration:none}',
                        '#splSearchPanel .spl-sname a:hover,#splSearchPanel .spl-ssub a:hover{color:var(--spl-accent,#1DB954)}',
                        '#splSearchPanel .spl-sbadge{display:inline-flex;align-items:center;justify-content:center;background:#b3b3b3;color:#121212;font-size:10.5px;font-weight:600;line-height:14px;border-radius:2px;padding:1px 5px;margin-right:5px;vertical-align:middle;font-family:inherit}',
                        '#splSearchPanel .spl-ssub{font-size:11px;color:rgba(255,255,255,.55);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.3;margin-top:2px}',
                        '#splSearchPanel .spl-stitle{font-size:12px;font-weight:700;color:rgba(255,255,255,.85);padding:8px 10px 4px}',
                        '#splSearchPanel .spl-sem{display:flex;align-items:center;justify-content:center;gap:8px;padding:14px;font-size:12px;color:rgba(255,255,255,.5)}',
                        '#splSearchPanel .spl-sem svg{width:16px;height:16px}',
                        '#splSearchPanel .spl-slist::-webkit-scrollbar{width:4px}',
                        '#splSearchPanel .spl-slist::-webkit-scrollbar-thumb{background:rgba(255,255,255,.2);border-radius:2px}'
                    ].join(' ');
                    var t = document.head || document.documentElement;
                    if(t) t.appendChild(st);
                }
            
                function api(operationName, hash, variables){
                    window.__splOwnCall = true;
                    var p = fetch('https://api-partner.spotify.com/pathfinder/v2/query', {
                        method: 'POST',
                        headers: {
                            'Authorization': window.spotAuthToken,
                            'Client-Token': window.spotCliToken || '',
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            variables: variables,
                            operationName: operationName,
                            extensions: { persistedQuery: { version: 1, sha256Hash: window.opHash(operationName, hash) } }
                        })
                    }).then(function(r){ return r.json(); });
                    window.__splOwnCall = false;
                    return p;
                }
            
                function findSearchAnchor(el){
                    if(!el || !el.closest) return null;
                    var icon = el.closest('button[data-testid="search-icon"]');
                    if(icon) return icon;
                    return el.closest('form[role="search"]');
                }
            
                function getAnchor(){
                    return document.querySelector('#global-nav-bar button[data-testid="search-icon"]') ||
                           document.querySelector('#global-nav-bar form[role="search"]');
                }
            
                function showPanelFor(anchor){
                    if(!panel) return;
                    if(!anchor || !anchor.isConnected) anchor = getAnchor();
                    if(panel.parentNode !== document.body) document.body.appendChild(panel);
                    var w = vw();
                    var pw = Math.min(360, w - 24);
                    panel.style.width = Math.round(pw) + 'px';
                    var r = null;
                    try { if(anchor && anchor.getBoundingClientRect) r = anchor.getBoundingClientRect(); } catch(e){}
                    if(r && r.width >= 2 && r.height >= 2 && r.bottom > 0){
                        panel.style.top = Math.round(r.bottom + 8) + 'px';
                        panel.style.left = Math.max(8, Math.min(Math.round(r.left), Math.round(w - pw - 8))) + 'px';
                    } else {
                        panel.style.top = '64px';
                        panel.style.left = Math.round(Math.max(8, (w - pw) / 2)) + 'px';
                    }
                    panel.style.display = 'flex';
                    document.documentElement.classList.add('spl-search-active');
                    if(pInput){
                        pInput.focus();
                        if(!pInput.value.trim()){ renderLoading(); doRecent(); }
                    }
                }
            
                function hidePanel(){
                    clearTimeout(debTimer);
                    if(panel) panel.style.display = 'none';
                    document.documentElement.classList.remove('spl-search-active');
                    lastQ = '';
                }
            
                function navTo(path){
                    if(!path) return;
                    try {
                        history.pushState({}, '', path);
                        window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                    } catch(e){
                        window.location.href = path;
                    }
                }
            
                function linkTo(path, text){
                    var a = document.createElement('a');
                    a.href = path || '#';
                    a.textContent = text || '';
                    a.className = 'spl-slink';
                    a.addEventListener('mousedown', function(e){ e.preventDefault(); e.stopPropagation(); });
                    a.addEventListener('click', function(e){
                        e.preventDefault();
                        e.stopPropagation();
                        hidePanel();
                        navTo(path);
                    });
                    return a;
                }
            
                function artistLinks(artists, sep){
                    var frag = document.createDocumentFragment();
                    var items = artists || [];
                    for(var i = 0; i < items.length; i++){
                        var it = items[i];
                        if(!it || !it.profile || !it.profile.name) continue;
                        if(i > 0 && sep) frag.appendChild(document.createTextNode(sep));
                        frag.appendChild(linkTo('/artist/' + stripId(it.uri, 'spotify:artist:'), it.profile.name));
                    }
                    return frag;
                }
            
                function showLinkFor(showData){
                    var sd = showData && (showData.data || showData);
                    if(!sd || !sd.name) return null;
                    return linkTo('/show/' + stripId(sd.uri, 'spotify:show:'), sd.name);
                }
            
                function favBtnFor(uri){
                    var b = document.createElement('button');
                    b.className = 'spl-sfav';
                    var saved = libCache.hasOwnProperty(uri) ? !!libCache[uri] : null;
                    var dirty = false;
                    function paint(){
                        b.innerHTML = saved ? ICONS.added : ICONS.add;
                        b.classList.toggle('saved', !!saved);
                        b.setAttribute('aria-label', saved ? 'Remove from Your Library' : 'Save to Your Library');
                    }
                    paint();
                    if(saved === null && uri) libQueue.push({ uri: uri, apply: function(v){
                        if(dirty) return;
                        saved = !!v; libCache[uri] = saved; paint();
                    }});
                    b.addEventListener('mousedown', function(e){ e.preventDefault(); e.stopPropagation(); });
                    b.addEventListener('click', function(e){
                        e.preventDefault(); e.stopPropagation();
                        if(!window.spotAuthToken || !uri) return;
                        dirty = true;
                        saved = !saved; libCache[uri] = saved; paint();
                        var op = saved ? 'addToLibrary' : 'removeFromLibrary';
                        api(op, LIB_TOGGLE_HASH, { libraryItemUris: [uri] })
                            .catch(function(){ saved = !saved; libCache[uri] = saved; paint(); });
                    });
                    return b;
                }
            
                function flushLibChecks(){
                    if(!libQueue.length) return;
                    if(!window.spotAuthToken){ libQueue.length = 0; return; }
                    var q = libQueue;
                    libQueue = [];
                    api('areEntitiesInLibrary', LIB_CHECK_HASH, { uris: q.map(function(x){ return x.uri; }) })
                        .then(function(d){
                            var l = d && d.data && d.data.lookup || [];
                            for(var i = 0; i < q.length; i++){
                                if(l[i] && l[i].data) q[i].apply(!!l[i].data.saved);
                            }
                        }).catch(function(){});
                }
            
                function setIcon(icon, url, kind){
                    if(url){
                        var im = document.createElement('img');
                        im.src = url;
                        icon.appendChild(im);
                    } else {
                        icon.innerHTML = iconFor(kind);
                    }
                }
            
                function rowFor(item){
                    var d = item.data || {};
                    var t = item.__typename || '';
            
                    var el = document.createElement('div');
                    el.className = 'spl-srow';
                    var icon = document.createElement('div');
                    icon.className = 'spl-sicon';
                    var tx = document.createElement('div');
                    tx.className = 'spl-stx';
                    var name = document.createElement('div');
                    name.className = 'spl-sname';
                    var sub = document.createElement('div');
                    sub.className = 'spl-ssub';
            
                    var kind = 'search', uri = d.uri || '', img = '', ico = 'search';
            
                    if(t === 'SearchAutoCompleteEntity'){
                        var text = d.text || '';
                        el.setAttribute('data-text', text);
                        var ac = document.createElement('a');
                        ac.href = '/search/' + encodeURIComponent(text);
                        ac.className = 'spl-slink';
                        ac.textContent = text;
                        ac.addEventListener('mousedown', function(e){ e.preventDefault(); e.stopPropagation(); });
                        ac.addEventListener('click', function(e){
                            e.preventDefault(); e.stopPropagation();
                            if(text && pInput){
                                pInput.value = text;
                                lastQ = text;
                                renderLoading();
                                doSearch(text);
                            }
                        });
                        name.appendChild(ac);
                        sub.textContent = 'Search';
                    } else if(t === 'ArtistResponseWrapper'){
                        kind = 'artist'; ico = 'artist';
                        img = pickImg(d.visuals && d.visuals.avatarImage && d.visuals.avatarImage.sources);
                        name.appendChild(linkTo('/artist/' + stripId(d.uri, 'spotify:artist:'), (d.profile && d.profile.name) || ''));
                        sub.textContent = 'Artist';
                    } else if(t === 'TrackResponseWrapper'){
                        kind = 'track'; ico = 'track';
                        img = pickImg(d.albumOfTrack && d.albumOfTrack.coverArt && d.albumOfTrack.coverArt.sources);
                        name.appendChild(linkTo('/track/' + stripId(d.uri, 'spotify:track:'), d.name || ''));
                        if(d.contentRating && d.contentRating.label === 'EXPLICIT'){
                            var eb = document.createElement('span');
                            eb.className = 'spl-sbadge';
                            eb.setAttribute('aria-label', 'Explicit');
                            eb.textContent = 'E';
                            sub.appendChild(eb);
                        }
                        sub.appendChild(document.createTextNode('Song'));
                        var arts = d.artists && d.artists.items || [];
                        if(arts.length){
                            sub.appendChild(document.createTextNode(' • '));
                            sub.appendChild(artistLinks(arts, ', '));
                        }
                    } else if(t === 'AlbumResponseWrapper'){
                        kind = 'album'; ico = 'album';
                        img = pickImg(d.coverArt && d.coverArt.sources);
                        name.appendChild(linkTo('/album/' + stripId(d.uri, 'spotify:album:'), d.name || ''));
                        var arts2 = d.artists && d.artists.items || [];
                        if(arts2.length){
                            sub.appendChild(artistLinks(arts2, ', '));
                            sub.appendChild(document.createTextNode(' · Album'));
                        } else {
                            sub.textContent = 'Album';
                        }
                    } else if(t === 'PlaylistResponseWrapper'){
                        kind = 'playlist'; ico = 'playlist';
                        img = pickImg(d.images && d.images.items && d.images.items[0] && d.images.items[0].sources) ||
                               pickImg(d.visualIdentity && d.visualIdentity.squareCoverImage && d.visualIdentity.squareCoverImage.sources) ||
                               pickImg(d.images && d.images.sources) ||
                               pickImg(d.visuals && d.visuals.image && d.visuals.image.sources);
                        name.appendChild(linkTo('/playlist/' + stripId(d.uri, 'spotify:playlist:'), d.name || ''));
                        var owner = d.ownerV2 && d.ownerV2.data;
                        if(owner && owner.name){
                            sub.appendChild(document.createTextNode('Playlist · '));
                            sub.appendChild(linkTo('/user/' + (owner.username || stripId(owner.uri, 'spotify:user:')), owner.name));
                        } else {
                            sub.textContent = 'Playlist';
                        }
                    } else if(t === 'PodcastEpisodeResponseWrapper' || t === 'EpisodeResponseWrapper'){
                        kind = 'episode'; ico = 'podcast';
                        img = pickImg(d.coverArt && d.coverArt.sources) || pickImg(d.images && d.images.sources);
                        name.appendChild(linkTo('/episode/' + stripId(d.uri, 'spotify:episode:'), d.name || ''));
                        var showL = showLinkFor(d.podcastV2 || d.show);
                        if(showL){
                            sub.appendChild(document.createTextNode('Episode · '));
                            sub.appendChild(showL);
                        } else {
                            sub.textContent = 'Episode';
                        }
                    } else if(t === 'ShowResponseWrapper'){
                        kind = 'show'; ico = 'podcast';
                        img = pickImg(d.coverArt && d.coverArt.sources) || pickImg(d.images && d.images.sources);
                        name.appendChild(linkTo('/show/' + stripId(d.uri, 'spotify:show:'), d.name || ''));
                        sub.textContent = 'Podcast';
                    } else {
                        el.setAttribute('data-text', d.text || d.name || '');
                        name.textContent = d.text || d.name || '';
                    }
            
                    el.setAttribute('data-kind', kind);
                    el.setAttribute('data-uri', uri);
                    setIcon(icon, img, ico);
            
                    tx.appendChild(name);
                    tx.appendChild(sub);
                    el.appendChild(icon);
                    el.appendChild(tx);
            
                    if(kind !== 'artist' && kind !== 'search' && uri){
                        var ply = document.createElement('div');
                        ply.className = 'spl-splay';
                        ply.innerHTML = ICONS.play;
                        icon.appendChild(ply);
                        icon.addEventListener('click', function(e){
                            e.preventDefault(); e.stopPropagation();
                            if(window.playFromUri){ window.playFromUri(uri); hidePanel(); }
                        });
                        el.appendChild(favBtnFor(uri));
                    }
                    el.addEventListener('mousedown', function(e){ e.preventDefault(); });
                    return el;
                }
            
                function renderLoading(){
                    if(!pList) return;
                    pList.innerHTML = '';
                    var em = document.createElement('div');
                    em.className = 'spl-sem';
                    em.innerHTML = iconFor('search') + '<span>Searching...</span>';
                    pList.appendChild(em);
                }
            
                function renderList(items, title, emptyMsg){
                    if(!pList) return;
                    pList.innerHTML = '';
                    if(!items || !items.length){
                        var em = document.createElement('div');
                        em.className = 'spl-sem';
                        em.textContent = emptyMsg;
                        pList.appendChild(em);
                        return;
                    }
                    if(title){
                        var h = document.createElement('div');
                        h.className = 'spl-stitle';
                        h.textContent = title;
                        pList.appendChild(h);
                    }
                    for(var i = 0; i < items.length; i++) pList.appendChild(rowFor(items[i]));
                    flushLibChecks();
                }
            
                function doRecent(){
                    var my = ++seq;
                    if(!window.spotAuthToken){ renderList(null, null, 'No recent searches'); return; }
                    api('recentSearches', RECENT_HASH, { limit: 50, includeAuthors: true, includeEpisodeContentRatingsV2: true })
                        .then(function(data){
                            if(my !== seq) return;
                            var items = data && data.data && data.data.recentSearches && data.data.recentSearches.recentSearchesItems && data.data.recentSearches.recentSearchesItems.items || [];
                            var flat = [];
                            for(var i = 0; i < items.length; i++){
                                if(items[i] && items[i].data) flat.push(items[i]);
                            }
                            renderList(flat, 'Recent searches', 'No recent searches');
                        })
                        .catch(function(){ if(my === seq) renderList(null, null, 'No recent searches'); });
                }
            
                function doSearch(q){
                    var my = ++seq;
                    if(!window.spotAuthToken){ renderList(null, null, 'No results'); return; }
                    api('searchSuggestions', HASH, { query: q, limit: 16, numberOfTopResults: 16, offset: 0, includeAuthors: true, includeAlbumPreReleases: true, includeEpisodeContentRatingsV2: true })
                        .then(function(data){
                            if(my !== seq) return;
                            var items = data && data.data && data.data.searchV2 && data.data.searchV2.topResultsV2 && data.data.searchV2.topResultsV2.itemsV2 || [];
                            var flat = [];
                            for(var i = 0; i < items.length; i++){
                                if(items[i] && items[i].item) flat.push(items[i].item);
                            }
                            renderList(flat, null, 'No results');
                        })
                        .catch(function(){ if(my === seq) renderList(null, null, 'No results'); });
                }
            
                function onInput(){
                    var v = pInput.value.trim();
                    if(!v){
                        lastQ = '';
                        clearTimeout(debTimer);
                        renderLoading();
                        doRecent();
                        return;
                    }
                    if(v === lastQ) return;
                    lastQ = v;
                    renderLoading();
                    clearTimeout(debTimer);
                    debTimer = setTimeout(function(){ doSearch(v); }, 220);
                }
            
                function buildPanel(){
                    if(panel) return;
                    panel = document.createElement('div');
                    panel.id = 'splSearchPanel';
            
                    var head = document.createElement('div');
                    head.className = 'spl-sph';
            
                    pInput = document.createElement('input');
                    pInput.className = 'spl-spi';
                    pInput.type = 'text';
                    pInput.placeholder = 'What do you want to play?';
                    pInput.setAttribute('spellcheck', 'false');
                    pInput.autocomplete = 'off';
            
                    var br = document.createElement('button');
                    br.className = 'spl-sbr';
                    br.setAttribute('aria-label', 'Browse');
                    br.innerHTML = iconFor('browse');
                    br.addEventListener('mousedown', function(e){ e.preventDefault(); });
                    br.addEventListener('click', function(e){
                        e.preventDefault(); e.stopPropagation();
                        var q = (pInput.value || '').trim();
                        hidePanel();
                        navTo(q ? '/search/' + encodeURIComponent(q) : '/search');
                    });
            
                    var x = document.createElement('button');
                    x.className = 'spl-spx';
                    x.setAttribute('aria-label', 'Clear search');
                    x.innerHTML = iconFor('clear');
                    x.addEventListener('mousedown', function(e){ e.preventDefault(); });
                    x.addEventListener('click', function(){
                        clearTimeout(debTimer);
                        pInput.value = '';
                        lastQ = '';
                        renderLoading();
                        doRecent();
                    });
            
                    head.appendChild(pInput);
                    head.appendChild(x);
                    head.appendChild(br);
            
                    pList = document.createElement('div');
                    pList.className = 'spl-slist';
            
                    panel.appendChild(head);
                    panel.appendChild(pList);
                    document.body.appendChild(panel);
            
                    pInput.addEventListener('input', onInput);
                    pInput.addEventListener('keydown', function(e){
                        if(e.key === 'Escape'){
                            if(pInput.value){
                                clearTimeout(debTimer);
                                pInput.value = ''; lastQ = '';
                                renderLoading(); doRecent();
                            } else hidePanel();
                        } else if(e.key === 'Enter'){
                            e.preventDefault();
                            var q = (pInput.value || '').trim();
                            if(q){ hidePanel(); navTo('/search/' + encodeURIComponent(q)); }
                        }
                    });
            
                    document.addEventListener('mousedown', function(e){
                        if(!panel || panel.style.display === 'none') return;
                        if(panel.contains(e.target)) return;
                        if(findSearchAnchor(e.target)) return;
                        hidePanel();
                    }, true);
                }
            
                    document.addEventListener('click', function(e){
                        var anchor = findSearchAnchor(e.target);
                        if(!anchor) return;
                        e.preventDefault();
                        e.stopPropagation();
                        if(panel && panel.style.display !== 'none'){ hidePanel(); }
                        else { buildPanel(); showPanelFor(anchor); }
                    }, true);
                
                css();
            })();
    """
}