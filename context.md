# Spotilol — Contexto de desarrollo

## Qué es Spotilol

Wrapper Android (WebView) de `open.spotify.com` (web player de Spotify) con inyecciones JS para reemplazar/mejorar la UI y el playback. Paquete: `com.project.lol`. Modo "Spotilol" = player custom (spl player); modo "Spotify Original" = player nativo con CSS hacks.

## Arquitectura clave

- **Inyecciones JS** (`app/src/main/java/com/project/lol/webview/injections/`):
  - `FetchOverride.kt` — captura `window.spotAuthToken` y `window.spotCliToken` de los requests reales del web player (NO hace falta auth propia). También detecta `spotDevId` y redirige `connect-state`/`melody/v1/msg`/`track-playback` por `mngFetch` (headers desktop) en modo normal.
  - `SpotifyWebViewClient.kt` — `injectPlayerControl()` arma el JS completo con todas las inyecciones. Ahí se registró `SearchOverlay.CONTENT`.
  - `SpotilolPlayer.kt` — el player custom (spl player): shuffle/repeat/mute con detección real via `aria-label`/`aria-disabled`.
  - `AndroidTracker.kt` / `MediaUpdater.kt` / `MediaNotificationService.kt` — estado en tiempo real hacia la media notification.
- **Bridge nativo**: `AndBridge` (addJavascriptInterface) → `SpotifyBridge.kt` (nFetch, wakeUp, openTimerDialog, etc.)

## ⚠️ WORK IN PROGRESS: Custom Search UI (SearchOverlay.kt)

**Estado: SIN TERMINAR.** El usuario reinstaló el APK con los últimos cambios (aún no validados en vivo).

### Qué hace

Reemplaza el searchbar nativo del topbar de Spotify por uno custom compacto:

- **Botón**: reutiliza la lupa nativa `#global-nav-bar button[data-testid="search-icon"]` como trigger (el form `[role=search]` y el input `search-input` quedan ocultos por CSS). El form se fuerza a 48×48 con flex centrado y `transform:none` en el wrapper del icono (si no, el icono queda desplazado a y:-24).
- **Panel**: `#splSearchPanel` (fixed overlay) con:
  - Header: botón circular (rueda) con lupa + input propio + botón X
  - Lista de resultados: filas compactas (ícono 36px con cover real o SVG + nombre + subtítulo), máx 12
- **Datos**: usa las queries GraphQL de pathfinder de Spotify (mismas del web player, con los tokens capturados):
  - `searchSuggestions` — hash `23f33ca50a0f4153dafc5cd1b4d1370db01b72130c2994bd0ffd07d5a7fee8f0` — variables: `{query, limit, numberOfTopResults, offset, includeAuthors, includeAlbumPreReleases, includeEpisodeContentRatingsV2}` → `data.searchV2.topResultsV2.itemsV2[].item` (SearchAutoCompleteEntity, ArtistResponseWrapper, TrackResponseWrapper, AlbumResponseWrapper, PlaylistResponseWrapper, Show/Episode)
  - `recentSearches` — hash `3ec071f88e403779d4da9bc5744feb9d64cd07d10daf1f966b912baadaa3d598` — variables: `{limit:50, includeAuthors, includeEpisodeContentRatingsV2}` → `data.recentSearches.recentSearchesItems.items[]` (estructura directa `{__typename, data}` — SIN wrapper `.item`, distinto a searchSuggestions)
- **Acciones por tipo** (click y Enter):
  - `track` → `playFromUri(uri)` (play directo)
  - `artist`/`album`/`playlist`/`show`/`episode` → navega a la página (`/artist/...` etc. via anchor click SPA)
  - `search` (autocomplete) → rellena el input y re-busca
  - **Enter** → actúa sobre el primer resultado del dropdown (NO navega a /search)
  - Escape → limpia input (vuelve a recientes) → cierra. X / click afuera / scroll del documento cierra (pero NO al scrollear dentro del panel)
- **Viewport**: el WebView corre con layout CSS de ~1920px pero la pantalla real es ~411dp (`visualViewport`). Todo el posicionamiento/tamaño usa `window.visualViewport.width` (NO `100vw` ni `innerWidth`), sino el panel sale gigante.

### Fixes ya aplicados en esta sesión (requieren validación tras rebuild)

1. Botón reutiliza la lupa nativa (antes un botón propio quedaba debajo de la sidebar "Open Your Library" en x:64)
2. `form[role=search]` forzado a 48×48 + `transform:none` en el wrapper del icono (fix del botón cortado en y:-24)
3. Panel dimensionado con `visualViewport` (máx 360px)
4. Header con botón circular (rueda) `spl-sph-btn`
5. Recent searches al abrir sin query (`doRecent()` con hash recientes)
6. Enter actúa sobre primer resultado (ya no navega a /search)

### Pendiente / a validar

- [ ] Validar en vivo (CDP) el flujo completo tras el rebuild: abrir → recientes → escribir → Enter → click por tipo
- [ ] Bug detectado en validación previa: los playlists de recentSearches salían vacíos en el render (probable carrera entre el APK viejo instalado y la inyección manual — revalidar con APK nuevo instalado)
- [ ] Decidir si el dropdown nativo de "recientes" (que aún se renderiza en el DOM) debe ocultarse siempre (CSS `html.spl-search-active` lo oculta solo con el panel activo)

## Cómo validar en vivo (CDP)

```bash
adb devices  # obtener serial (ej: 100.90.82.98:45359)
adb -s $SERIAL shell pidof com.project.lol   # PID actual (cambia en cada reinicio)
adb -s $SERIAL forward tcp:9222 localabstract:webview_devtools_remote_$PID
curl -s http://localhost:9222/json   # ver páginas, seleccionar la de open.spotify.com
```

Importante: el APK instalado inyecta su propia versión de SearchOverlay al cargar. Si inyectás otra versión por CDP encima, hay carrera entre ambas (listeners duplicados en el icono). Para validar cambios nuevos: **recompilar + reinstalar + reiniciar app + rehacer forward**.

## Commits recientes

- `1f4de02` Simplify Sleep Timer
- `0c8c28f` fix playback state conflict on service restart
- `060f94b` fix shuffle/mute detection + shuffle in media notification

## Pendiente general (fuera de esta tarea)

- Modo certificate vs normal: solo en normal el server imponía estado viejo al reproducir (ya fixeado con track-playback por nFetch)
- RPC Esperanto (settings del playback): no viable sin MITM del app nativo — descartado
