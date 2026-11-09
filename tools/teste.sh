#!/bin/bash
# Teste no emulador: abre o LumeStarTV, fotografa a abertura, envia uma lista de teste
# "pelo celular" (como o usuário faria), navega pelas abas e toca um canal.
# Qualquer falha cria teste/falhou.txt.
mkdir -p teste
PKG=com.lumestar.tv
LISTA="https://raw.githubusercontent.com/${GITHUB_REPOSITORY:-sergiobarbozajr/lumestartv}/${GITHUB_SHA:-main}/tools/teste-lista.m3u"
shot() { adb exec-out screencap -p > "teste/$1.png" 2>/dev/null || true; }
key() { adb shell input keyevent "$1"; sleep "${2:-0.6}"; }

adb install -r LumeStarTV.apk > teste/instalar.txt 2>&1 || { echo "nao instalou" > teste/falhou.txt; exit 0; }
adb logcat -c
adb shell am start -n $PKG/.MainActivity --es modo tv > /dev/null
START=$(date +%s%N)
for i in $(seq -w 1 14); do
  sleep 0.9
  shot "abertura_$i"
  echo "abertura_$i $(( ($(date +%s%N) - START) / 1000000 )) ms" >> teste/tempos.txt
done
sleep 5
shot "inicio_vazio"
adb shell dumpsys gfxinfo $PKG reset > /dev/null 2>&1 || true
abre() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity --ez sem_abertura true --es modo tv "$@" > /dev/null; sleep 6; }
cel() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity --ez sem_abertura true --es modo celular "$@" > /dev/null; sleep 7; }
toque() { adb shell input tap "$1" "$2"; sleep "${3:-1.2}"; }

# "Enviar pelo celular": abre Configurações › Contas IPTV (liga a página na rede) e envia a lista de teste
abre --es pagina config
key KEYCODE_DPAD_RIGHT 1
for i in $(seq 1 12); do key KEYCODE_DPAD_UP 0.3; done
sleep 2
shot "config_contas"
adb forward tcp:8765 tcp:8765
URLQ=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$LISTA")
for t in 1 2 3; do
  curl -s -m 15 -d "tipo=lista&nome=Lista+de+teste&url=$URLQ" http://127.0.0.1:8765/ > teste/celular_resposta.html && grep -q "Enviado" teste/celular_resposta.html && break
  echo "tentativa $t sem resposta" >> teste/celular_tentativas.txt; adb shell netstat -ltn 2>/dev/null | grep 8765 >> teste/celular_tentativas.txt; sleep 4
done
grep -q "Enviado" teste/celular_resposta.html && echo "celular: enviado" >> teste/resumo_extra.txt || echo "celular: FALHOU" >> teste/resumo_extra.txt
# imagem do tema Anime enviada pelo "celular" (formulário com arquivo)
python3 - <<'PY'
import zlib, struct
W, H = 640, 360
rows = bytearray()
for y in range(H):
    rows.append(0)
    for x in range(W):
        t = x / W
        r, g, b = int(26 + (232 - 26) * t), int(58 + (112 - 58) * t), int(138 + (42 - 138) * t)
        if (x - 210) ** 2 + (y - 165) ** 2 < 60 ** 2: r, g, b = 127, 214, 255
        rows += bytes((r, g, b))
def chunk(k, d): return struct.pack('>I', len(d)) + k + d + struct.pack('>I', zlib.crc32(k + d) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(rows), 6)) + chunk(b'IEND', b'')
open('teste/tema.png', 'wb').write(png)
PY
curl -s -m 30 -F "imagem=@teste/tema.png;type=image/png" http://127.0.0.1:8765/ > teste/tema_resposta.html
grep -q "Imagem recebida" teste/tema_resposta.html && echo "tema anime: recebido" >> teste/resumo_extra.txt || echo "tema anime: FALHOU" >> teste/resumo_extra.txt
sleep 2; shot "tema_anime"
sleep 20
shot "lista_recebida"

for p in inicio aovivo filmes series lancamentos lista pesquisa; do abre --es pagina $p; shot "pagina_$p"; done
abre --es pagina aovivo
key KEYCODE_DPAD_RIGHT 1.5
shot "aovivo_foco"
key KEYCODE_DPAD_CENTER 1
sleep 14
shot "reproduzindo_canal"
sleep 6; key KEYCODE_DPAD_UP 3
shot "canal_seguinte_tv"
key KEYCODE_BACK 2
abre --es pagina series
key KEYCODE_DPAD_RIGHT 1.2
key KEYCODE_DPAD_CENTER 3
shot "serie_detalhe"
key KEYCODE_BACK 1.5
abre --es pagina filmes
key KEYCODE_DPAD_RIGHT 1.2
key KEYCODE_DPAD_CENTER 3
shot "filme_detalhe"
key KEYCODE_BACK 1.5
abre --es abrir "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
sleep 5
shot "reproduzindo_filme"
key KEYCODE_DPAD_CENTER 1.5
shot "filme_pausado"
for i in 1 2 3 4; do key KEYCODE_DPAD_RIGHT 0.4; done
key KEYCODE_DPAD_CENTER 1.5
shot "player_painel_imagem"
key KEYCODE_BACK 1
for i in 1 2 3; do key KEYCODE_DPAD_RIGHT 0.4; done
key KEYCODE_DPAD_CENTER 1.5
shot "player_painel_som"
key KEYCODE_BACK 1
key KEYCODE_DPAD_RIGHT 0.4
key KEYCODE_DPAD_CENTER 1.5
shot "player_painel_legendas"
key KEYCODE_BACK 1; key KEYCODE_BACK 1; key KEYCODE_BACK 1.5
# configurações novas da entrega 3
abre --es pagina config
key KEYCODE_DPAD_RIGHT 1
for i in $(seq 1 12); do key KEYCODE_DPAD_UP 0.3; done
key KEYCODE_DPAD_DOWN 0.4; key KEYCODE_DPAD_DOWN 1.5
shot "config_tmdb"
key KEYCODE_DPAD_DOWN 1
shot "config_transmissao"
for i in 1 2 3; do key KEYCODE_DPAD_DOWN 0.4; done
sleep 0.6; shot "config_video"
for i in 1 2 3 4 5 6 7; do key KEYCODE_DPAD_DOWN 0.4; done
sleep 2; shot "config_rede"
key KEYCODE_DPAD_UP 0.4; key KEYCODE_DPAD_UP 0.4; key KEYCODE_DPAD_UP 0.4; key KEYCODE_DPAD_UP 0.4
sleep 1; shot "config_tema"
key KEYCODE_DPAD_RIGHT 0.5; key KEYCODE_DPAD_RIGHT 0.5; key KEYCODE_DPAD_CENTER 1.5
shot "tema_aurora_config"
abre --es pagina inicio
shot "tema_aurora_inicio"
abre --es pagina radio
sleep 6; shot "radio"
abre --es pagina streaming
sleep 3; shot "streaming_logos"
abre --es pagina cfg:sobre
sleep 3; shot "config_sobre"
abre --es pagina cfg:ai
sleep 3; shot "config_ia"
for u in https://dash.akamaized.net/akamai/streamroot/050714/Spring_4Ktest.mpd https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8; do echo "$u $(curl -s -o /dev/null -w '%{http_code}' $u)"; done > teste/url4k.txt
abre --es abrir desempenho
sleep 55; shot "teste_desempenho"
adb logcat -d | grep -iE "FATAL|VideoFrameProcessingException|PixelCopy" | tail -8 > teste/desempenho.txt
abre --es pagina inicio
adb logcat -d | grep -iE "ExoPlayer|MediaCodec.*(error|fail)|PlaybackException" | tail -20 > teste/player_log.txt

# lista gigante (como a de um usuário real): 40 mil filmes/séries e 5 mil canais
python3 - <<'PY'
import random
random.seed(7)
g=["Ação","Comédia","Drama","Terror","Animação","Documentário","Romance","Ficção"]
with open("grande.m3u","w") as f:
    f.write("#EXTM3U\n")
    for i in range(5000):
        f.write(f'#EXTINF:-1 tvg-logo="" group-title="Canais | {g[i%8]}",Canal {i} HD\nhttp://127.0.0.1:9/live/{i}.m3u8\n')
    for i in range(40000):
        y=1980+i%45
        f.write(f'#EXTINF:-1 tvg-logo="" group-title="Filmes | {g[i%8]}",Filme Teste {i} ({y}) [4K] Dublado\nhttp://127.0.0.1:9/movie/{i}.mp4\n')
PY
(python3 -m http.server 8099 > /dev/null 2>&1 &) ; sleep 1
adb reverse tcp:8099 tcp:8099
abre --es pagina config
key KEYCODE_DPAD_RIGHT 1
for i in $(seq 1 14); do key KEYCODE_DPAD_UP 0.3; done
sleep 2
adb forward tcp:8765 tcp:8765
curl -s -m 15 -d "tipo=lista&nome=Gigante&url=http%3A%2F%2F127.0.0.1%3A8099%2Fgrande.m3u" http://127.0.0.1:8765/ > /dev/null
sleep 40
shot "grande_lista"
adb logcat -c
abre --es pagina inicio
sleep 8
shot "grande_inicio"
key KEYCODE_DPAD_RIGHT 1; key KEYCODE_DPAD_DOWN 1; key KEYCODE_DPAD_DOWN 1
shot "grande_navegando"
abre --es pagina filmes
sleep 4
shot "grande_filmes"
adb logcat -d | grep -E "ANR in|FATAL|Skipped [0-9]{3,} frames" > teste/grande_problemas.txt
if grep -q "ANR in\|FATAL" teste/grande_problemas.txt; then echo "lista grande: travou ou fechou" >> teste/falhou.txt; cat teste/grande_problemas.txt >> teste/falhou.txt; fi
adb shell pidof $PKG > /dev/null || echo "lista grande: o app fechou" >> teste/falhou.txt

adb shell dumpsys gfxinfo $PKG > teste/gfx_total.txt 2>&1 || true
# tema com foto (como no AstroLume) e Movimento 60 Hz
abre --es tema galaxia --es pagina inicio
sleep 3; shot "tema_galaxia_inicio"
abre --es pagina config
key KEYCODE_DPAD_RIGHT 1
sleep 1; shot "tema_galaxia_config"
abre --es abrir "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
sleep 7; key KEYCODE_DPAD_CENTER 1; shot "filme_ia_cena"
adb logcat -d | grep -iE "VideoFrameProcessingException|AiSharpen|GlUtil" | tail -5 > teste/ia_cena.txt
abre --es tema cosmos --es pagina inicio

# ---- versão de celular (toque) ----
adb shell settings put system accelerometer_rotation 0
cel
shot "celular_inicio"
cel --es pagina aovivo
shot "celular_aovivo"
toque 300 500 4
read W2 H2 <<< $(adb shell wm size | grep -oE "[0-9]+x[0-9]+" | tail -1 | tr x ' ')
toque $((H2*9/10)) $((W2/2)) 3
shot "canal_seguinte_celular"
cel --es pagina series
shot "celular_series"
read W H <<< $(adb shell wm size | grep -oE "[0-9]+x[0-9]+" | tail -1 | tr x ' ')
toque $((W/6)) $((H*35/100)) 2
shot "celular_detalhe"
cel --es pagina mais
shot "celular_mais"
cel --es pagina cfg:listas
shot "celular_config_listas"
cel --es abrir "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
sleep 3
shot "celular_video"

adb logcat -d > teste/logcat.txt 2>&1 || true

{
  echo "== Navegacao pelo controle (GPU simulada do emulador) =="
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" teste/gfx_total.txt | head -5
  cat teste/resumo_extra.txt 2>/dev/null
  grep -h "LumeStar" teste/logcat.txt | tail -5
  cat teste/celular_tentativas.txt 2>/dev/null
} > teste/resumo.txt

if ! adb shell pidof $PKG > /dev/null; then echo "o app fechou sozinho" >> teste/falhou.txt; fi
if grep -q "FATAL EXCEPTION" teste/logcat.txt; then
  grep -A25 "FATAL EXCEPTION" teste/logcat.txt | head -50 >> teste/falhou.txt
fi
exit 0
