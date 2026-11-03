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
adb shell am start -n $PKG/.MainActivity > /dev/null
START=$(date +%s%N)
for i in $(seq -w 1 14); do
  sleep 0.9
  shot "abertura_$i"
  echo "abertura_$i $(( ($(date +%s%N) - START) / 1000000 )) ms" >> teste/tempos.txt
done
sleep 5
shot "inicio_vazio"
adb shell dumpsys gfxinfo $PKG reset > /dev/null 2>&1 || true
abre() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity --ez sem_abertura true "$@" > /dev/null; sleep 6; }

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
sleep 20
shot "lista_recebida"

for p in inicio aovivo filmes series lancamentos lista pesquisa; do abre --es pagina $p; shot "pagina_$p"; done
abre --es pagina aovivo
key KEYCODE_DPAD_RIGHT 1.5
shot "aovivo_foco"
key KEYCODE_DPAD_CENTER 1
sleep 14
shot "reproduzindo_canal"
key KEYCODE_BACK 2
abre --es pagina series
key KEYCODE_DPAD_RIGHT 1.2
key KEYCODE_DPAD_CENTER 3
shot "serie_detalhe"
key KEYCODE_BACK 1.5
abre --es abrir "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
sleep 10
shot "reproduzindo_filme"
key KEYCODE_DPAD_CENTER 1.5
shot "filme_pausado"
adb logcat -d | grep -iE "ExoPlayer|MediaCodec.*(error|fail)|PlaybackException" | tail -20 > teste/player_log.txt

adb shell dumpsys gfxinfo $PKG > teste/gfx_total.txt 2>&1 || true
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
