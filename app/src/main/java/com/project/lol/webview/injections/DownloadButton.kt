package com.project.lol.webview.injections

object DownloadButton {
    const val CONTENT = """
            window.splDoDownload = function(){
                var id = window.splTrackId || null;
                if (!id) {
                    try {
                        var a = document.querySelector('[data-testid="now-playing-widget"] a[href*="/track/"]');
                        if (a) {
                            var m = (a.getAttribute('href') || '').match(/\/track\/([a-zA-Z0-9]+)/);
                            if (m) id = m[1];
                        }
                    } catch (e) {}
                }
                if (!id) id = window.__curTrackId || null;

                if (!id) {
                    AndBridge.deferMessage('Track not ready');
                    return;
                }

                var title = '', artist = '', cover = '';
                try {
                    var tEl = document.querySelector('a[data-testid=context-item-link]');
                    if (tEl) title = (tEl.textContent || '').trim();
                    var aEl = document.querySelector('a[data-testid=context-item-info-artist]');
                    if (!aEl) aEl = document.querySelector('a[data-testid=context-item-info-show]');
                    if (aEl) artist = (aEl.textContent || '').trim();
                    var img = document.querySelector('[data-testid="now-playing-widget"] img[data-testid="cover-art-image"]');
                    if (img && img.src) {
                        cover = img.src.replace(/ab67616d0000[0-9a-f]{4}/i, 'ab67616d000082c1');
                    }
                } catch (e) {}
                if (!title)  title  = window.track  || window.__curTrackName  || '';
                if (!artist) artist = window.artist || window.__curTrackArtist || '';
                if (!cover)  cover  = window.cover   || window.__curTrackCover  || '';

                var dur = 0;
                try { if (typeof duration !== 'undefined' && duration) dur = parseInt(duration, 10) || 0; } catch (e) {}

                var payload = JSON.stringify({
                    trackId: id,
                    title: title,
                    artist: artist,
                    album: window.__curTrackAlbum || '',
                    cover: cover,
                    durationSec: dur
                });
                AndBridge.downloadTrack(payload);
            };
            window.splAddDownloadBtn = function(){
                if(typeof window.dlBtn !== 'undefined') return;
                var lyBtn = document.querySelector('button[data-testid=lyrics-button]:not(.splf)');
                var queueBtn = document.querySelector('button[data-testid=control-button-queue]:not(.splf)');
                var anchorBtn = lyBtn || queueBtn;
                if(!anchorBtn) return;
                if(anchorBtn === lyBtn) lyBtn.classList.add('splf');
                var btn = document.createElement('button');
                btn.className = 'npbtn';
                btn.title = 'Download track';
                btn.innerHTML = '<svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M8 1a1 1 0 0 1 1 1v6.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L7 8.586V2a1 1 0 0 1 1-1zM2 13a1 1 0 0 1 1-1h10a1 1 0 1 1 0 2H3a1 1 0 0 1-1-1z"/></svg>';
                btn.onclick = function(){ splDoDownload(); };
                anchorBtn.before(btn);
                window.dlBtn = btn;
            };
            setInterval(splAddDownloadBtn, 5000);

    """
}