#!/bin/bash
# Teste no emulador: abre o LumeStarTV, fotografa a abertura e o menu,
# navega pelo controle e mede a fluidez. Qualquer falha cria teste/falhou.txt.
mkdir -p teste
PKG=com.lumestar.tv
shot() { adb exec-out screencap -p > "teste/$1.png" 2>/dev/null || true; }
key() { adb shell input keyevent "$1"; sleep "${2:-0.4}"; }

adb install -r LumeStarTV.apk > teste/instalar.txt 2>&1 || { echo "nao instalou" > teste/falhou.txt; exit 0; }
adb logcat -c
adb shell dumpsys gfxinfo $PKG reset > /dev/null 2>&1 || true
adb shell am start -n $PKG/.MainActivity > /dev/null
START=$(date +%s%N)
for i in $(seq -w 1 16); do
  sleep 0.9
  shot "abertura_$i"
  echo "abertura_$i $(( ($(date +%s%N) - START) / 1000000 )) ms" >> teste/tempos.txt
done
sleep 4
shot "inicio"
adb shell dumpsys gfxinfo $PKG > teste/gfx_abertura.txt 2>&1 || true

# navegação pelo controle
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN 1.2
shot "menu_aberto"
for i in $(seq 1 13); do key KEYCODE_DPAD_DOWN 0.25; done
sleep 1.2
shot "menu_config"
key KEYCODE_DPAD_RIGHT 1
shot "config_lista"
for i in $(seq 1 16); do key KEYCODE_DPAD_DOWN 0.25; done
sleep 1
shot "config_fim"
key KEYCODE_BACK 0.8
key KEYCODE_BACK 0.8
key KEYCODE_BACK 1
shot "sair_pergunta"
key KEYCODE_BACK 1
shot "sair_cancelado"

adb shell dumpsys gfxinfo $PKG > teste/gfx_total.txt 2>&1 || true
adb logcat -d > teste/logcat.txt 2>&1 || true

{
  echo "== LumeStarTV no emulador =="
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" teste/gfx_total.txt | head -6
} > teste/resumo.txt

if ! adb shell pidof $PKG > /dev/null; then echo "o app fechou sozinho" >> teste/falhou.txt; fi
if grep -q "FATAL EXCEPTION" teste/logcat.txt; then
  grep -A20 "FATAL EXCEPTION" teste/logcat.txt | head -40 >> teste/falhou.txt
fi
exit 0
