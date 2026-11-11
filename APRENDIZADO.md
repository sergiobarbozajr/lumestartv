# Registro de aprendizagem e correção — LumeStarTV

Cada fechamento ou falha vira uma entrada: **sintoma → causa → correção → versão**.
Aparelho de referência do piso: **Fire TV Stick 2ª geração (tank, AFTT)** — Fire OS 5.2.9.5, Android 5.1.1 (API 22), MT8127 quad Cortex-A7 32-bit, 895 MB de RAM.

Como investigar um fechamento:
1. No app: **Configurações › Sobre o aparelho** mostra o último erro (`CrashLog`).
2. Pelo ADB: `adb logcat -d -v time > crash.txt` logo depois do fechamento.
3. No CI: o job **compatibilidade** grava no ramo `lint` a lista de chamadas acima do minSdk (`lint NewApi`). Meta: **0 erros**.

| # | Sintoma | Causa | Correção | Versão |
|---|---|---|---|---|
| 1 | APK não instala (`INSTALL_FAILED_OLDER_SDK`) | `minSdk = 24` (Android 7) | `minSdk = 21` | 0.8.1 |
| 2 | Fecha no início da abertura, sem erro no log; sistema mata outros serviços | Gradientes criados a cada quadro: no Android < 8 a memória nativa só é liberada pelo coletor do Java → falta de RAM | **Modo leve** (`Perf`): em Android < 7 ou ≤ 1,5 GB pula abertura, buraco de minhoca e teste de GPU | 0.8.2 |
| 3 | Abertura e menu sem som | `AudioTrack.Builder` só existe no Android 6+ | Construtor antigo do `AudioTrack` no Android < 6 | 0.8.2 |
| 4 | (prevenção) Catálogo/TMDB fechariam | `Map.putIfAbsent` / `removeIf` só no Android 7+ | `coreLibraryDesugaring` | 0.8.2 |
| 5 | Fecha em Configurações › Rede | `ConnectivityManager.activeNetwork` só no Android 6+ | Rede ativa via `activeNetworkInfo` + `allNetworks` | 0.8.3 |
| 6 | Notícias vazias | `Html.fromHtml(html, flags)` só no Android 7+ | Versão de 1 parâmetro no Android < 7 | 0.8.3 |
| 7 | Conta IPTV não loga (senha salva vazia) | Chave AES no Keystore (`KeyGenParameterSpec`) só no Android 6+ | Android 5: chave RSA do Keystore embrulha uma AES | 0.8.4 |
| 8 | Fecha ao atualizar pelo app | `canRequestPackageInstalls` só no Android 8+; instalador só aceita `content://` no Android 7+ | Checagem por versão; `file://` legível no Android < 7 | 0.8.4 |
| 9 | Modo leve ligaria sem necessidade em TVs novas de 1,5 GB | Regra cautelosa demais (RAM ≤ 1,5 GB); o vazamento só existe no Android < 8 | Automático = Android < 8 ou aparelho "pouca memória"; opção **Desempenho: Automático / Completo / Leve** | 0.8.5 |
| 10 | Celular: destaque ocupando a tela em aparelho pequeno; tablet travado em pé | Medidas fixas (420/250 dp, 2 colunas) e orientação fixa | Classe de tela (compacto < 360 dp, normal, expandido ≥ 600 dp) ajusta cartões, destaque, detalhes e grades; tablet gira | 0.8.5 |
| 11 | Teste de desempenho: "o vídeo de teste não tocou" nos dois modos (TCL A400M, Android 14) | Vídeo 4K de teste sem imagem em 30 s: servidor lento/fora do ar sem erro, ou decodificador sem o 4K no clássico; a troca para a reserva só acontecia em erro de rede/formato | 20 s sem imagem ou erro de decodificador no clássico → próximo vídeo de reserva; espera até 60 s; relatório mostra o código do erro | 0.9.8 |
| 12 | TCL Smart TV Pro (Realtek, Android 14): vídeo não toca por causa do som (sem erro claro ou erro de som) | A TV diz aceitar AC3/E-AC3 "direto" pelo sistema, mas o som não abre ou trava e segura a imagem | Erro de saída de som (5xxx) ou tempo parado 10 s com som → reabre com som em PCM e lembra nesta TV; erro de decodificador de áudio → tenta de novo uma vez (PCM não muda nada); se ainda falhar, toca sem som e oferece converter o áudio (o convertido religa o som). Opção **Saída de som: Automático / Direto / PCM** desfaz a escolha | 0.9.8 |
| 13 | Android 10 ou mais antigo: vídeo, som e controle travam juntos durante a reprodução | IA de imagem (efeito na placa) ligava antes do diagnóstico ou em placa lenta; o diagnóstico de GPU podia rodar com vídeo aberto; foto do quadro a cada 2 s na linha da tela mesmo sem IA | Android < 11: IA só depois do diagnóstico e com placa nível ≥ 1; diagnóstico espera o vídeo fechar; análise de cena só com a IA ligada | 0.9.8 |

Lições gerais:
- Testar sempre no aparelho mais fraco do público (o tank), não só no emulador (API 30).
- Toda API nova precisa de `if (Build.VERSION.SDK_INT >= N)`; o lint NewApi no CI acha as que faltarem.
- `catch (e: Exception)` não pega `NoSuchMethodError`/`NoClassDefFoundError` (são `Error`): em código que depende de versão, use `Throwable`.
- Animação pesada em hardware fraco: degradar (modo leve) em vez de manter dois apps, sempre com opção manual para o usuário.
- Layout: medidas proporcionais à tela (com mínimo e máximo), nunca altura fixa grande.
