package com.project.lol.webview.injections

object VideoFullscreen {
    const val CONTENT = """
        (function(){
            if(window.__splVfs) return;
            window.__splVfs=true;
            var ACTIVE=false, MISSING=0;
            function vid(){
                var v=document.querySelector('.VideoPlayer__container video');
                if(!v||v.ended) return null;
                return v;
            }
            function inNowPlaying(el){
                if(!el||!el.closest) return false;
                return !!el.closest('aside[data-testid="now-playing-bar"],[data-testid="now-playing-bar"],[data-testid="now-playing-widget"],[video-player-npv]');
            }
            function isTrigger(el){
                if(!el||el.id==='spl-vfs-exit') return false;
                if(el.matches('[data-testid="fullscreen-mode-button"]')) return true;
                var a=(el.getAttribute('aria-label')||'').trim().toLowerCase();
                if(a==='full screen'||a==='enter full screen') return true;
                if(a==='exit full screen') return ACTIVE;
                return a==='expand now playing view'&&inNowPlaying(el);
            }
            function exitButton(show){
                var b=document.getElementById('spl-vfs-exit');
                if(!show){
                    if(b&&b.parentNode) b.parentNode.removeChild(b);
                    return;
                }
                if(b) return;
                var c=document.querySelector('.VideoPlayer__container');
                if(!c) return;
                b=document.createElement('button');
                b.id='spl-vfs-exit';
                b.type='button';
                b.setAttribute('aria-label','Exit full screen');
                b.textContent=String.fromCharCode(10005);
                b.style.cssText='position:fixed;top:14px;right:14px;z-index:2147483647;width:44px;height:44px;padding:0;border:0;border-radius:50%;background:rgba(0,0,0,.6);color:#fff;font-size:18px;line-height:44px;text-align:center;cursor:pointer;-webkit-tap-highlight-color:transparent;';
                b.addEventListener('click',function(ev){ev.preventDefault();ev.stopImmediatePropagation();exitFs();},true);
                c.appendChild(b);
            }
            function enterFs(){
                if(ACTIVE) return false;
                if(!vid()) return false;
                ACTIVE=true;MISSING=0;
                try{ window.__splPipFillVideo(true); }catch(e){}
                exitButton(true);
                try{ AndBridge.enterVideoFullscreen(); }catch(e){}
                return true;
            }
            function exitFs(){
                if(!ACTIVE) return false;
                ACTIVE=false;MISSING=0;
                exitButton(false);
                try{ window.__splPipFillVideo(false); }catch(e){}
                try{ AndBridge.exitVideoFullscreen(); }catch(e){}
                return true;
            }
            window.__splVfsEnter=enterFs;
            window.__splVfsExit=exitFs;
            window.__splVfsActive=function(){ return ACTIVE; };
            document.addEventListener('click',function(e){
                var el=e.target&&e.target.closest?e.target.closest('button,[role="button"]'):null;
                if(!el||!isTrigger(el)) return;
                if(ACTIVE){ e.preventDefault();e.stopPropagation();exitFs();return; }
                if(!vid()) return;
                e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
                enterFs();
            },true);
            document.addEventListener('keydown',function(e){
                if(e.key==='Escape'&&ACTIVE){ e.preventDefault();exitFs(); }
            },true);
            setInterval(function(){
                if(!ACTIVE) return;
                if(vid()){ MISSING=0; return; }
                if(++MISSING>=2) exitFs();
            },1000);
        })();
    """
}
