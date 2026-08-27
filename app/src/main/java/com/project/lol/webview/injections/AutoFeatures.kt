package com.project.lol.webview.injections

object AutoFeatures {
    const val CONTENT = """
            window.splAutoPlay = function(){
                if(window.autoPlayMode==='disabled') return;
                if(window.__splApRan) return;
                window.__splApRan = true;
                window.__splApActive = true;

                var attempts = 0;
                var noBtnTicks = 0;

                function done(unlocked){
                    window.__splApActive = false;
                    if(unlocked) window.__splUnlocked = true;
                    clearInterval(iv);
                }

                var tick = function(){
                    var pb = window.pBtn;
                    if(!pb){
                        if(++noBtnTicks > 15) done(false);
                        return;
                    }
                    if(reqPause){ done(false); return; }
                    if(pb.getAttribute('aria-label')!=='Play'){
                        done(true);
                        return;
                    }
                    if(attempts >= 4){ done(false); return; }
                    attempts++;
                    pb.click();
                };

                var iv = setInterval(tick, 4000);
                tick();
            };

            window.addAutoFeatures = function(){
                if(afint) clearInterval(afint);
                afint = setInterval(function(){
                    if(window.closeNpPref) closeNowPlay();
                    var ft = document.querySelector('aside div.encore-bright-accent-set button');
                    if(ft) {
                        ft.click();
                        setTimeout(function(){
                            var cb = document.querySelector('aside ul[role=list] li[role=listitem] div[role=button]');
                            if(cb) cb.click();
                        },500);
                    }
                    if(window.autoPlayMode==='permanent' && 'pBtn' in window && window.__splUnlocked && !reqPause && !ulFlag && !window.__splApActive && pBtn.getAttribute('aria-label')==='Play') {
                        pBtn.click();
                    }
                },5000);
            };
    """
}