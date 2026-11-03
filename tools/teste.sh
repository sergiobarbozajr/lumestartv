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

# Configurações (15 para baixo a partir do Início) e entra em Contas IPTV: liga o "Enviar pelo celular"
for i in $(seq 1 15); do key KEYCODE_DPAD_DOWN 0.5; done
sleep 1.5
key KEYCODE_DPAD_RIGHT 2
shot "config_contas"
adb forward tcp:8765 tcp:8765
curl -s -m 15 -d "tipo=lista&nome=Lista+de+teste&url=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$LISTA")" http://127.0.0.1:8765/ > teste/celular_resposta.html || echo "sem resposta" > teste/celular_resposta.html
grep -q "Enviado" teste/celular_resposta.html && echo "celular: enviado" >> teste/resumo_extra.txt || echo "celular: FALHOU" >> teste/resumo_extra.txt
sleep 20
shot "lista_recebida"

# volta ao menu e vai para TV ao vivo (2 para baixo a partir do Início)
key KEYCODE_BACK 1
for i in $(seq 1 13); do key KEYCODE_DPAD_UP 0.5; done
sleep 2
shot "aovivo"
key KEYCODE_DPAD_RIGHT 1
shot "aovivo_foco"
key KEYCODE_DPAD_CENTER 1
sleep 14
shot "reproduzindo"
adb logcat -d | grep -iE "ExoPlayer|MediaCodec.*(error|fail)" | tail -20 > teste/player_log.txt
key KEYCODE_BACK 2
# filmes (6 a partir do Início = 4 abaixo de TV ao vivo)
key KEYCODE_BACK 1
for i in $(seq 1 4); do key KEYCODE_DPAD_DOWN 0.5; done
sleep 3
shot "filmes"
key KEYCODE_DPAD_RIGHT 1
key KEYCODE_DPAD_DOWN 1
shot "filmes_fileiras"

adb shell dumpsys gfxinfo $PKG > teste/gfx_total.txt 2>&1 || true
adb logcat -d > teste/logcat.txt 2>&1 || true

{
  echo "== Navegacao pelo controle (GPU simulada do emulador) =="
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" teste/gfx_total.txt | head -5
  cat teste/resumo_extra.txt 2>/dev/null
} > teste/resumo.txt

if ! adb shell pidof $PKG > /dev/null; then echo "o app fechou sozinho" >> teste/falhou.txt; fi
if grep -q "FATAL EXCEPTION" teste/logcat.txt; then
  grep -A25 "FATAL EXCEPTION" teste/logcat.txt | head -50 >> teste/falhou.txt
fi
exit 0
