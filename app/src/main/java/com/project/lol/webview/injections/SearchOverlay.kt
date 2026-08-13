package com.project.lol.webview.injections

object SearchOverlay {
    const val CONTENT = """
            (function(){
                if(window.splSearchInit) return;
                window.splSearchInit = true;

                var panel = null, pInput = null, debTimer = null, seq = 0;
                var lastQ = '', anchoredBtn = null;

                var HASH = '23f33ca50a0f4153dafc5cd1b4d1370db01b72130c2994bd0ffd07d5a7fee8f0';
                var RECENT_HASH = '3ec071f88e403779d4da9bc5744feb9d64cd07d10daf1f966b912baadaa3d598';

                var ICONS = {
                    search: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>',
                    artist: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-3.31 0-6 1.79-6 4v2h12v-2c0-2.21-2.69-4-6-4z"/></svg>',
                    track: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 3v9.28a4.39 4.39 0 0 0-1.5-.26c-2.49 0-4.5 1.79-4.5 4s2.01 4 4.5 4 4.5-1.79 4.5-4V7h4V3h-7z"/></svg>',
                    album: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 12m-3.5 0a3.5 3.5 0 1 0 7 0 3.5 3.5 0 1 0-7 0zM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 17a7 7 0 1 1 0-14 7 7 0 0 1 0 14z"/></svg>',
                    playlist: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M14.5 4.5v11.1a3.5 3.5 0 1 0 1 2.4V8.5h4V4.5h-5zM4 5h9v1.5H4V5zm0 4h9v1.5H4V9zm0 4h5v1.5H4v-1.5z"/></svg>',
                    podcast: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2z"/></svg>',
                    close: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>'
                };

                function iconFor(kind){ return ICONS[kind] || ICONS.search; }

                function pickImg(sources){
                    if(!sources || !sources.length) return '';
                    var best = null;
                    for(var i=0;i<sources.length;i++){
                        var s = sources[i];
                        if(!s || !s.url) continue;
                        var w = s.width || 0;
                        if(!best) { best = s; continue; }
                        if(w >= 40 && w < (best.width || 9999)) best = s;
                        if(!best.width && w) best = s;
                    }
                    return best ? best.url : '';
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
                        '#global-nav-bar form[role="search"] .e-10750-form-input-icon__icon{position:static!important;top:auto!important;transform:none!important}',
                        'html.spl-search-active [data-testid="search-dropdown"],html.spl-search-active [data-testid="search-page-searchbar-searchbar-dropdown"]{display:none!important}',
                        '#splSearchPanel{position:fixed;z-index:99999;background:rgba(24,24,24,.98);border:1px solid rgba(255,255,255,.08);border-radius:14px;box-shadow:0 8px 32px rgba(0,0,0,.6);display:none;flex-direction:column;overflow:hidden;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;color:#fff}',
                        '#splSearchPanel .spl-sph{display:flex;align-items:center;gap:10px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,.06)}',
                        '#splSearchPanel .spl-sph-btn{box-sizing:border-box;width:32px;height:32px;flex-shrink:0;border-radius:50%;background:rgba(255,255,255,.08);display:flex;align-items:center;justify-content:center;color:#fff}',
                        '#splSearchPanel .spl-sph-btn svg{width:16px;height:16px}',
                        '#splSearchPanel .spl-spi{flex:1;min-width:0;background:transparent;border:none;outline:none;color:#fff;font-size:14px;height:32px}',
                        '#splSearchPanel .spl-spi::placeholder{color:rgba(255,255,255,.4)}',
                        '#splSearchPanel .spl-spx{box-sizing:border-box;width:30px;height:30px;flex-shrink:0;background:none;border:none;border-radius:50%;color:rgba(255,255,255,.7);cursor:pointer;display:flex;align-items:center;justify-content:center;padding:0;transition:background .15s}',
                        '#splSearchPanel .spl-spx:hover{background:rgba(255,255,255,.12);color:#fff}',
                        '#splSearchPanel .spl-spx svg{width:16px;height:16px}',
                        '#splSearchPanel .spl-slist{max-height:55vh;overflow-y:auto;padding:6px;overscroll-behavior:contain}',
                        '#splSearchPanel .spl-srow{display:flex;align-items:center;gap:10px;padding:8px;border-radius:8px;cursor:pointer;min-height:44px;box-sizing:border-box}',
                        '#splSearchPanel .spl-srow:hover{background:rgba(255,255,255,.08)}',
                        '#splSearchPanel .spl-sicon{width:36px;height:36px;flex-shrink:0;border-radius:6px;overflow:hidden;background:#282828;display:flex;align-items:center;justify-content:center;color:#b3b3b3}',
                        '#splSearchPanel .spl-sicon img{width:100%;height:100%;object-fit:cover}',
                        '#splSearchPanel .spl-sicon svg{width:18px;height:18px}',
                        '#splSearchPanel .spl-stx{flex:1;min-width:0;overflow:hidden}',
                        '#splSearchPanel .spl-sname{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.3}',
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

                function showPanel(){
                    if(!panel || !anchoredBtn) return;
                    var r = anchoredBtn.getBoundingClientRect();
                    var w = vw();
                    var pw = Math.min(360, w - 24);
                    panel.style.width = Math.round(pw) + 'px';
                    var left = Math.max(8, Math.min(Math.round(r.left), Math.round(w - pw - 8)));
                    panel.style.top = Math.round(r.bottom + 8) + 'px';
                    panel.style.left = left + 'px';
                    panel.style.display = 'flex';
                    document.documentElement.classList.add('spl-search-active');
                    if(pInput){
                        pInput.focus();
                        if(!pInput.value.trim()){ renderLoading(); doRecent(); }
                    }
                }

                function hidePanel(){
                    if(panel) panel.style.display = 'none';
                    document.documentElement.classList.remove('spl-search-active');
                    lastQ = '';
                }

                function navTo(path){
                    if(!path) return;
                    var a = document.createElement('a');
                    a.href = path;
                    a.style.display = 'none';
                    document.body.appendChild(a);
                    a.click();
                    setTimeout(function(){ if(a.parentNode) a.parentNode.removeChild(a); }, 50);
                }

                function actRow(row){
                    var kind = row.getAttribute('data-kind');
                    var uri = row.getAttribute('data-uri') || '';
                    hidePanel();
                    if(kind === 'track' && window.playFromUri){
                        window.playFromUri(uri);
                    } else if(kind === 'search'){
                        var text = row.getAttribute('data-text') || '';
                        if(text && pInput){
                            pInput.value = text;
                            doSearch(text);
                            showPanel();
                        }
                    } else {
                        var p = row.getAttribute('data-path') || '';
                        navTo(p);
                    }
                }

                function rowFor(item){
                    var t = item.__typename || '';
                    var d = item.data || {};
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

                    if(t === 'SearchAutoCompleteEntity'){
                        var text = d.text || '';
                        el.setAttribute('data-kind','search');
                        el.setAttribute('data-text',text);
                        el.setAttribute('data-uri',d.uri || '');
                        icon.innerHTML = iconFor('search');
                        name.textContent = text;
                        sub.textContent = 'Search';
                    } else if(t === 'ArtistResponseWrapper'){
                        el.setAttribute('data-kind','artist');
                        el.setAttribute('data-uri',d.uri || '');
                        el.setAttribute('data-path','/artist/' + (d.uri||'').replace('spotify:artist:',''));
                        var av = pickImg(d.visuals && d.visuals.avatarImage && d.visuals.avatarImage.sources);
                        if(av){ var im=document.createElement('img'); im.src=av; icon.appendChild(im); } else { icon.innerHTML = iconFor('artist'); }
                        name.textContent = (d.profile && d.profile.name) || '';
                        sub.textContent = 'Artist';
                    } else if(t === 'TrackResponseWrapper'){
                        el.setAttribute('data-kind','track');
                        el.setAttribute('data-uri',d.uri || '');
                        var cv = pickImg(d.albumOfTrack && d.albumOfTrack.coverArt && d.albumOfTrack.coverArt.sources);
                        if(cv){ var im2=document.createElement('img'); im2.src=cv; icon.appendChild(im2); } else { icon.innerHTML = iconFor('track'); }
                        name.textContent = d.name || '';
                        var arts = (d.artists && d.artists.items || []).map(function(a){ return a.profile && a.profile.name; }).filter(Boolean).join(', ');
                        sub.textContent = arts || 'Song';
                    } else if(t === 'AlbumResponseWrapper'){
                        el.setAttribute('data-kind','album');
                        el.setAttribute('data-uri',d.uri || '');
                        el.setAttribute('data-path','/album/' + (d.uri||'').replace('spotify:album:',''));
                        var cv2 = pickImg(d.coverArt && d.coverArt.sources);
                        if(cv2){ var im3=document.createElement('img'); im3.src=cv2; icon.appendChild(im3); } else { icon.innerHTML = iconFor('album'); }
                        name.textContent = d.name || '';
                        var arts2 = (d.artists && d.artists.items || []).map(function(a){ return a.profile && a.profile.name; }).filter(Boolean).join(', ');
                        sub.textContent = (arts2 ? arts2 + ' · ' : '') + 'Album';
                    } else if(t === 'PlaylistResponseWrapper'){
                        el.setAttribute('data-kind','playlist');
                        el.setAttribute('data-uri',d.uri || '');
                        el.setAttribute('data-path','/playlist/' + (d.uri||'').replace('spotify:playlist:',''));
                        var cv3 = pickImg(d.images && d.images.sources) || pickImg(d.visuals && d.visuals.image && d.visuals.image.sources);
                        if(cv3){ var im4=document.createElement('img'); im4.src=cv3; icon.appendChild(im4); } else { icon.innerHTML = iconFor('playlist'); }
                        name.textContent = d.name || '';
                        sub.textContent = 'Playlist';
                    } else if(t === 'PodcastEpisodeResponseWrapper' || t === 'EpisodeResponseWrapper'){
                        el.setAttribute('data-kind','episode');
                        el.setAttribute('data-uri',d.uri || '');
                        el.setAttribute('data-path','/episode/' + (d.uri||'').replace(/^spotify:episode:/,''));
                        var cv4 = pickImg(d.coverArt && d.coverArt.sources) || pickImg(d.images && d.images.sources);
                        if(cv4){ var im5=document.createElement('img'); im5.src=cv4; icon.appendChild(im5); } else { icon.innerHTML = iconFor('podcast'); }
                        name.textContent = d.name || '';
                        var showName = d.show && d.show.name;
                        sub.textContent = (showName ? showName + ' · ' : '') + 'Episode';
                    } else if(t === 'ShowResponseWrapper'){
                        el.setAttribute('data-kind','show');
                        el.setAttribute('data-uri',d.uri || '');
                        el.setAttribute('data-path','/show/' + (d.uri||'').replace('spotify:show:',''));
                        var cv5 = pickImg(d.coverArt && d.coverArt.sources) || pickImg(d.images && d.images.sources);
                        if(cv5){ var im6=document.createElement('img'); im6.src=cv5; icon.appendChild(im6); } else { icon.innerHTML = iconFor('podcast'); }
                        name.textContent = d.name || '';
                        sub.textContent = 'Podcast';
                    } else {
                        el.setAttribute('data-kind','search');
                        el.setAttribute('data-text',(d.text||d.name||''));
                        el.setAttribute('data-uri',d.uri || '');
                        icon.innerHTML = iconFor('search');
                        name.textContent = d.text || d.name || '';
                        sub.textContent = '';
                    }

                    tx.appendChild(name);
                    tx.appendChild(sub);
                    el.appendChild(icon);
                    el.appendChild(tx);
                    el.addEventListener('mousedown', function(e){ e.preventDefault(); });
                    el.addEventListener('click', function(){ actRow(el); });
                    return el;
                }

                function renderLoading(){
                    var list = panel ? panel.querySelector('.spl-slist') : null;
                    if(!list) return;
                    list.innerHTML = '';
                    var em = document.createElement('div');
                    em.className = 'spl-sem';
                    em.innerHTML = iconFor('search') + '<span>Searching...</span>';
                    list.appendChild(em);
                }

                function renderResults(items){
                    var list = panel ? panel.querySelector('.spl-slist') : null;
                    if(!list) return;
                    list.innerHTML = '';
                    if(!items || !items.length){
                        var em = document.createElement('div');
                        em.className = 'spl-sem';
                        em.textContent = 'No results';
                        list.appendChild(em);
                        return;
                    }
                    for(var i=0;i<items.length;i++){
                        list.appendChild(rowFor(items[i]));
                    }
                }

                function renderRecent(items){
                    var list = panel ? panel.querySelector('.spl-slist') : null;
                    if(!list) return;
                    list.innerHTML = '';
                    if(!items || !items.length){
                        var em = document.createElement('div');
                        em.className = 'spl-sem';
                        em.textContent = 'No recent searches';
                        list.appendChild(em);
                        return;
                    }
                    var t = document.createElement('div');
                    t.className = 'spl-stitle';
                    t.textContent = 'Recent searches';
                    list.appendChild(t);
                    for(var i=0;i<items.length;i++){
                        list.appendChild(rowFor(items[i]));
                    }
                }

                function doRecent(){
                    var my = ++seq;
                    if(!window.spotAuthToken){ return; }
                    fetch('https://api-partner.spotify.com/pathfinder/v2/query', {
                        method:'POST',
                        headers:{
                            'Authorization': window.spotAuthToken,
                            'Client-Token': window.spotCliToken || '',
                            'Content-Type':'application/json'
                        },
                        body: JSON.stringify({
                            variables:{limit:50,includeAuthors:true,includeEpisodeContentRatingsV2:true},
                            operationName:'recentSearches',
                            extensions:{persistedQuery:{version:1,sha256Hash:RECENT_HASH}}
                        })
                    }).then(function(r){ return r.json(); }).then(function(data){
                        if(my !== seq) return;
                        var items = data && data.data && data.data.recentSearches && data.data.recentSearches.recentSearchesItems && data.data.recentSearches.recentSearchesItems.items || [];
                        var flat = [];
                        for(var i=0;i<items.length;i++){
                            if(items[i] && items[i].data) flat.push(items[i]);
                        }
                        renderRecent(flat);
                    }).catch(function(){});
                }

                function doSearch(q){
                    var my = ++seq;
                    if(!window.spotAuthToken){ return; }
                    fetch('https://api-partner.spotify.com/pathfinder/v2/query', {
                        method:'POST',
                        headers:{
                            'Authorization': window.spotAuthToken,
                            'Client-Token': window.spotCliToken || '',
                            'Content-Type':'application/json'
                        },
                        body: JSON.stringify({
                            variables:{query:q,limit:12,numberOfTopResults:12,offset:0,includeAuthors:true,includeAlbumPreReleases:true,includeEpisodeContentRatingsV2:true},
                            operationName:'searchSuggestions',
                            extensions:{persistedQuery:{version:1,sha256Hash:HASH}}
                        })
                    }).then(function(r){ return r.json(); }).then(function(data){
                        if(my !== seq) return;
                        var items = data && data.data && data.data.searchV2 && data.data.searchV2.topResultsV2 && data.data.searchV2.topResultsV2.itemsV2 || [];
                        var flat = [];
                        for(var i=0;i<items.length;i++){
                            if(items[i] && items[i].item) flat.push(items[i].item);
                        }
                        renderResults(flat);
                    }).catch(function(){});
                }

                function onInput(){
                    var v = pInput.value.trim();
                    if(!v){ lastQ=''; renderResults([]); return; }
                    renderLoading();
                    if(v === lastQ) return;
                    lastQ = v;
                    clearTimeout(debTimer);
                    debTimer = setTimeout(function(){ doSearch(v); }, 220);
                }

                function buildPanel(){
                    if(panel) return;
                    panel = document.createElement('div');
                    panel.id = 'splSearchPanel';

                    var head = document.createElement('div');
                    head.className = 'spl-sph';
                    var hb = document.createElement('div');
                    hb.className = 'spl-sph-btn';
                    hb.innerHTML = iconFor('search');
                    head.appendChild(hb);

                    pInput = document.createElement('input');
                    pInput.className = 'spl-spi';
                    pInput.type = 'text';
                    pInput.placeholder = 'What do you want to play?';
                    pInput.setAttribute('spellcheck','false');
                    pInput.autocomplete = 'off';

                    var x = document.createElement('button');
                    x.className = 'spl-spx';
                    x.setAttribute('aria-label','Close search');
                    x.innerHTML = iconFor('close');
                    x.addEventListener('mousedown', function(e){ e.preventDefault(); });
                    x.addEventListener('click', function(){ hidePanel(); });

                    head.appendChild(pInput);
                    head.appendChild(x);

                    var list = document.createElement('div');
                    list.className = 'spl-slist';

                    panel.appendChild(head);
                    panel.appendChild(list);
                    document.body.appendChild(panel);

                    pInput.addEventListener('input', onInput);
                    pInput.addEventListener('keydown', function(e){
                        if(e.key === 'Escape'){
                            if(pInput.value){ pInput.value=''; lastQ=''; renderLoading(); doRecent(); }
                            else hidePanel();
                        } else if(e.key === 'Enter'){
                            var first = panel.querySelector('.spl-slist .spl-srow');
                            if(first){ actRow(first); }
                        }
                    });

                    document.addEventListener('mousedown', function(e){
                        if(panel.style.display !== 'none' && !panel.contains(e.target) && !(anchoredBtn && anchoredBtn.contains(e.target))){
                            hidePanel();
                        }
                    }, true);
                    document.addEventListener('scroll', function(e){
                        if(panel.style.display !== 'none'){
                            var t = e.target;
                            if(!(t === panel || (panel.contains && panel.contains(t)))) hidePanel();
                        }
                    }, true);
                }

                function bindSearchIcon(){
                    var icon = document.querySelector('#global-nav-bar button[data-testid="search-icon"]');
                    if(!icon || icon._splSearch) return;
                    icon._splSearch = true;
                    anchoredBtn = icon;
                    icon.addEventListener('click', function(e){
                        e.preventDefault();
                        e.stopPropagation();
                        if(panel && panel.style.display !== 'none'){ hidePanel(); }
                        else { buildPanel(); showPanel(); }
                    }, true);
                }

                css();
                var int = setInterval(function(){
                    bindSearchIcon();
                }, 2000);
            })();
        
    """
}
