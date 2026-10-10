// Mobil uygulamanın yükleme testi. Gelen veri sayılır ve atılır; Storage'a yazılmaz.
const MAX_BYTES = 1024 * 1024;
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };

Deno.serve(async (request) => {
  const reply = (status = 200, value = {}) => new Response(JSON.stringify(value), { status, headers });
  if (request.method !== "GET" && request.method !== "POST") return reply(405, { error: "method_not_allowed" });
  const authorization = request.headers.get("Authorization") ?? "";
  if (!/^Bearer\s+\S+$/i.test(authorization)) return reply(401, { error: "authentication_required" });

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !key) return reply(503, { error: "configuration_missing" });
  // Gateway doğrulaması kapalı kurulsa dahi access_token Auth sunucusunda doğrulanır.
  try {
    const auth = await fetch(`${url}/auth/v1/user`, { headers: { Authorization: authorization, apikey: key } });
    if (!auth.ok) return reply(auth.status >= 500 ? 503 : 401, { error: "authentication_failed" });
    const user = await auth.json();
    if (typeof user.app_metadata?.ogrenci_id !== "string" || !user.app_metadata.ogrenci_id.trim()) {
      return reply(403, { error: "student_required" });
    }
  } catch {
    return reply(503, { error: "authentication_unavailable" });
  }

  if (request.method === "GET") return reply(200, { ready: true });
  if (request.headers.get("Content-Type")?.split(";")[0] !== "application/octet-stream") {
    return reply(415, { error: "binary_body_required" });
  }
  const declared = Number(request.headers.get("Content-Length") ?? 0);
  if (declared > MAX_BYTES) return reply(413, { error: "body_too_large" });
  if (!request.body) return reply(400, { error: "empty_body" });
  const reader = request.body.getReader();
  let bytes = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      bytes += chunk.value.byteLength;
      if (bytes > MAX_BYTES) {
        await reader.cancel();
        return reply(413, { error: "body_too_large" });
      }
    }
  } catch {
    return reply(400, { error: "incomplete_body" });
  } finally {
    reader.releaseLock();
  }
  return bytes > 0 ? reply(200, { bytes }) : reply(400, { error: "empty_body" });
});
