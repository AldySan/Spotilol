package com.project.lol.webview.injections

object AutoFeatures {
    const val CONTENT = """
            window.armAutoPlay = function(){
                if(window.__splApArmed) return;
                window.__splApArmed = true;
                if(window.autoPlayMode==='disabled') return;
                window.__splApActive = true;
                var attempts = 0;
                var noBtnTicks = 0;
                var confirmed = false;
                var labelObs = null;

                function done(){
                    window.__splApActive = false;
                    clearInterval(iv);
                    if(labelObs){ try{ labelObs.disconnect(); }catch(e){} labelObs = null; }
                }

                function watchLabel(pb){
                    if(labelObs || !pb || !window.MutationObserver) return;
                    try{
                        labelObs = new MutationObserver(function(){
                            confirmed = true;
                            done();
                        });
                        labelObs.observe(pb, { attributes: true, attributeFilter: ['aria-label'] });
                    }catch(e){}
                }

                var tick = function(){
                    var pb = window.pBtn;
                    if(confirmed) return;
                    if(!pb){
                        if(++noBtnTicks > 15) done();
                        return;
                    }
                    watchLabel(pb);
                    if(pb.getAttribute('aria-label')!=='Play'){
                        done();
                        return;
                    }
                    if(reqPause || ulFlag){
                        done();
                        return;
                    }
                    if(attempts >= 4){
                        done();
                        return;
                    }
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
                    if(window.autoPlayMode==='permanent' && 'pBtn' in window && !reqPause && !ulFlag && !window.__splApActive && pBtn.getAttribute('aria-label')==='Play') {
                        pBtn.click();
                    }
                },5000);
            };
    """
}