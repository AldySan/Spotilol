package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Worker Neutralizer.
 * GitHub: https://github.com/AldySan
 *
 * Unregisters Spotify's service worker and prevents re-registration.
 * The SW intercepts ALL network requests to check its cache map - pure
 * overhead on a mobile WebView that always has internet and has its own
 * HTTP cache + optional MITM proxy.
 *
 * Injected in onPageStarted so it runs before Spotify's scripts try to
 * register the SW via Workbox. The register() override returns a rejected
 * promise - Workbox catches this and continues without SW, falling back
 * to network-first behavior (which is what we want).
 *
 * Also throttles aggressive 250ms setInterval polling to 1000ms. The
 * 250ms timers from web-player.js:215359 are non-core UI polling that
 * gets cleared/recreated in bursts. Throttling reduces CPU wakeups by
 * 75% while keeping the feature functional. CORE intervals (progress
 * tracking at 500ms, Connect polling at 1000ms) are untouched.
 */
object WorkerNeutralize {
    const val CONTENT = """
        (function(){
            if(window.__splWorkerNeutralized) return;
            window.__splWorkerNeutralized = true;

            /* === Service Worker: unregister + prevent re-registration === */
            if(navigator.serviceWorker){
                try {
                    navigator.serviceWorker.register = function(){
                        return Promise.reject(new Error('SW blocked by Spotilol'));
                    };
                } catch(e){}
                try {
                    navigator.serviceWorker.getRegistrations().then(function(regs){
                        regs.forEach(function(reg){
                            reg.unregister().then(function(ok){
                                if(ok){
                                    try{AndBridge.dbg('s','SW unregistered: '+reg.scope)}catch(e){}
                                }
                            }).catch(function(){});
                        });
                    }).catch(function(){});
                } catch(e){}
            }

            /* === Interval throttle: 250ms -> 1000ms ===
               Only affects the aggressive web-player polling timers.
               _startProgressTracking (500ms) and ensurePolling (1000ms)
               are in a different file (vendor~web-player) and have different
               delays, so they pass through untouched. */
            try {
                var origSI = window.setInterval.bind(window);
                window.setInterval = function(fn, delay){
                    if(delay === 250) delay = 1000;
                    return origSI(fn, delay);
                };
            } catch(e){}
        })();
    """
}