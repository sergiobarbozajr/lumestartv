/**
 * Serviço de erros do LumeStarTV (Cloudflare Worker).
 * Recebe o relatório do app e abre (ou atualiza) um registro em Issues do GitHub.
 * A chave do GitHub fica só aqui, como segredo do Cloudflare (GH_TOKEN); nunca vai no APK.
 */
const MAX = 20000;

function gh(env, path, init = {}) {
  return fetch("https://api.github.com" + path, {
    ...init,
    headers: {
      "Authorization": "Bearer " + env.GH_TOKEN,
      "Accept": "application/vnd.github+json",
      "User-Agent": "lumestar-erros",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
    },
  });
}

const limpo = (s, n) => String(s ?? "").replace(/[`<>]/g, "").slice(0, n);

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "GET") return new Response("LumeStarTV · serviço de erros no ar");
    if (req.method !== "POST" || url.pathname !== "/erro") return new Response("não encontrado", { status: 404 });
    if (req.headers.get("x-lume") !== "lumestartv-1") return new Response("recusado", { status: 403 });
    const bruto = await req.text();
    if (bruto.length > MAX) return new Response("grande demais", { status: 413 });
    let d;
    try { d = JSON.parse(bruto); } catch { return new Response("json inválido", { status: 400 }); }

    const sig = String(d.assinatura || "").replace(/[^a-f0-9]/gi, "").slice(0, 12) || "semassinatura";
    const tipo = limpo(d.tipo || "erro", 30);
    const titulo = `[erro ${sig}] ${tipo}: ${limpo(d.resumo, 90)}`;
    const corpo = [
      `**Versão:** ${limpo(d.versao, 20)} · **Aparelho:** ${limpo(d.aparelho, 60)} ${d.modelo ? "(" + limpo(d.modelo, 30) + ")" : ""}`,
      `**Android:** ${limpo(d.android, 30)} · **Chip:** ${limpo(d.chip, 30)}`,
      `**Desempenho:** ${limpo(d.desempenho, 120)} · **IA/saída:** ${limpo(d.ia, 80)}`,
      `**Quando:** ${new Date().toISOString()}`,
      "",
      "```",
      limpo(d.texto, 6000),
      "```",
    ].join("\n");

    const repo = env.REPO;
    const q = encodeURIComponent(`repo:${repo} is:issue is:open in:title "erro ${sig}"`);
    const busca = await gh(env, `/search/issues?q=${q}&per_page=1`);
    const achado = busca.ok ? (await busca.json()).items?.[0] : null;

    if (achado) {
      if ((achado.comments || 0) >= 30) return new Response("ok (registro cheio)");
      const r = await gh(env, `/repos/${repo}/issues/${achado.number}/comments`, { method: "POST", body: JSON.stringify({ body: "Aconteceu de novo:\n\n" + corpo }) });
      return new Response(r.ok ? "ok (repetido)" : "falhou", { status: r.ok ? 200 : 502 });
    }
    const r = await gh(env, `/repos/${repo}/issues`, { method: "POST", body: JSON.stringify({ title: titulo, body: corpo + "\n\n_Registro aberto automaticamente pelo app._" }) });
    return new Response(r.ok ? "ok (novo)" : "falhou " + r.status, { status: r.ok ? 200 : 502 });
  },
};
