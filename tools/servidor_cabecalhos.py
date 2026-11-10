"""Servidor de teste: só entrega o vídeo se o pedido trouxer o cabeçalho certo.

/ref/...  exige Referer: https://lumestar.test/
/ua/...   exige User-Agent: LumeStudio/1.0
/both/... exige os dois
Grava cada pedido em cabecalhos.log (caminho, código, User-Agent, Referer).
"""
import http.server, os, sys

RAIZ = sys.argv[1]
LOG = sys.argv[2]
REF = "https://lumestar.test/"
UA = "LumeStudio/1.0"


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        p = self.path.split("?")[0]
        ua = self.headers.get("User-Agent", ""); ref = self.headers.get("Referer", "")
        partes = p.strip("/").split("/")
        regra = partes[0] if partes else ""
        ok = True
        if regra in ("ref", "both") and ref != REF: ok = False
        if regra in ("ua", "both") and ua != UA: ok = False
        arq = os.path.join(RAIZ, "hls", partes[-1]) if regra in ("ref", "ua", "both", "livre") else os.path.join(RAIZ, p.strip("/"))
        code = 200 if ok and os.path.isfile(arq) else (403 if not ok else 404)
        with open(LOG, "a") as f:
            f.write(f"{code} {p} UA={ua} REF={ref}\n")
        if code != 200:
            self.send_response(code); self.end_headers(); return
        dados = open(arq, "rb").read()
        tipo = "application/vnd.apple.mpegurl" if arq.endswith(".m3u8") else "video/mp2t" if arq.endswith(".ts") else "image/png" if arq.endswith(".png") else "application/octet-stream"
        self.send_response(200); self.send_header("Content-Type", tipo); self.send_header("Content-Length", str(len(dados))); self.end_headers()
        self.wfile.write(dados)

    def log_message(self, *a):
        pass


http.server.ThreadingHTTPServer(("0.0.0.0", 8098), H).serve_forever()
