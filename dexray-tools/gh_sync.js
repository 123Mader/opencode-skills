#!/usr/bin/env node
// ============================================================
// DeXRay GitHub 同步工具 — 任意项目推送/同步到 GitHub
// 用法:
//   node gh_sync.js push <本地目录> <owner/repo> [目标子目录]
//   node gh_sync.js list-projects           # 列出可同步的项目
//   node gh_sync.js sync-all <owner/repo>   # 同步所有已知项目
// 配置: gh_token.txt (token) + gh_projects.json (项目清单)
// ============================================================
const fs = require('fs');
const path = require('path');

const TOKEN_FILE = '/storage/emulated/0/MT2/apks/gh_token.txt';
const PROJECTS_FILE = '/storage/emulated/0/MT2/apks/gh_projects.json';
const API = 'https://api.github.com';
const TOKEN = fs.readFileSync(TOKEN_FILE, 'utf8').trim();
const HEADERS = { 'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json', 'User-Agent': 'dexray-sync' };

async function api(url, body, method = 'POST', retries = 5) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetch(API + url, { method, headers: HEADERS, body: body ? JSON.stringify(body) : undefined });
      const j = await res.json().catch(() => ({}));
      if (res.ok) return j;
      if (res.status === 403 || res.status === 429) {
        const wait = 5000 * Math.pow(2, attempt);
        console.log(`  限流, 等待 ${(wait/1000).toFixed(0)}s...`);
        if (attempt < retries) { await new Promise(r => setTimeout(r, wait)); continue; }
      }
      throw new Error(`API ${res.status}: ${j.message || 'unknown'}`);
    } catch (e) {
      if (attempt < retries && /fetch failed|ECONNRESET|ETIMEDOUT|ECONNREFUSED/.test(e.message)) {
        await new Promise(r => setTimeout(r, 5000 * (attempt + 1)));
        continue;
      }
      throw e;
    }
  }
}

function collectFiles(dir, prefix) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (['build', '.gradle', 'node_modules', '.git', '.idea'].includes(entry.name)) continue;
      out.push(...collectFiles(full, prefix + entry.name + '/'));
    } else {
      const st = fs.statSync(full);
      if (st.size > 1500000) { console.log('  ⏭ 跳过大文件: ' + (prefix + entry.name) + ' (' + (st.size/1048576).toFixed(1) + 'MB)'); continue; }
      out.push({ path: (prefix + entry.name).replace(/^\//, ''), content: fs.readFileSync(full).toString('base64') });
    }
  }
  return out;
}

async function pushProject(srcDir, repo, destDir) {
  console.log(`📦 ${path.basename(srcDir)} → ${repo}${destDir ? '/' + destDir : ''}`);
  await api(`/repos/${repo}`, null, 'GET');
  const files = collectFiles(srcDir, destDir ? destDir + '/' : '');
  console.log(`📄 ${files.length} 个文件`);
  const entries = [];
  for (let i = 0; i < files.length; i += 40) {
    const batch = files.slice(i, i + 40);
    for (const f of batch) {
      const b = await api(`/repos/${repo}/git/blobs`, { content: f.content, encoding: 'base64' });
      entries.push({ path: f.path, mode: '100644', type: 'blob', sha: b.sha });
    }
  }
  const branch = 'main';
  let baseTree = null, parent = null;
  try {
    const b = await api(`/repos/${repo}/branches/${branch}`, null, 'GET');
    baseTree = b.commit.commit.tree.sha; parent = b.commit.sha;
  } catch {}
  const tree = await api(`/repos/${repo}/git/trees`, { tree: entries, base_tree: baseTree });
  const commit = await api(`/repos/${repo}/git/commits`, {
    message: `sync: ${path.basename(srcDir)} (${new Date().toISOString().slice(0,10)})`,
    tree: tree.sha, parents: parent ? [parent] : [],
  });
  await api(`/repos/${repo}/git/refs/heads/${branch}`, { sha: commit.sha, force: true }, 'PATCH');
  console.log(`🎉 同步完成: https://github.com/${repo}/tree/${branch}/${destDir}`);
  return true;
}

// 已知项目清单 (可在此添加更多项目)
const KNOWN_PROJECTS = {
  'xinan-app': '/storage/emulated/0/MT2/心安项目',
  'dexray-tools': '/storage/emulated/0/MT2/apks/dexray',
};

(async () => {
  const [cmd, arg1, arg2, arg3] = process.argv.slice(2);
  switch (cmd) {
    case 'push': {
      const src = arg1, repo = arg2, dest = arg3;
      if (!src || !repo) { console.error('用法: gh_sync.js push <目录> <owner/repo> [子目录]'); process.exit(1); }
      await pushProject(src, repo, dest);
      break;
    }
    case 'list-projects':
      console.log('可同步项目:');
      for (const [name, dir] of Object.entries(KNOWN_PROJECTS)) {
        console.log(`  ${name}: ${dir} ${fs.existsSync(dir) ? '✅' : '❌缺失'}`);
      }
      break;
    case 'sync-all': {
      const repo = arg1;
      if (!repo) { console.error('用法: gh_sync.js sync-all <owner/repo>'); process.exit(1); }
      for (const [name, dir] of Object.entries(KNOWN_PROJECTS)) {
        if (!fs.existsSync(dir)) { console.log(`⚠️ 跳过 ${name} (目录缺失)`); continue; }
        try { await pushProject(dir, repo, name); }
        catch (e) { console.log(`❌ ${name} 失败: ${e.message}`); }
      }
      break;
    }
    default:
      console.log(`用法:
  node gh_sync.js push <目录> <owner/repo> [子目录]
  node gh_sync.js list-projects
  node gh_sync.js sync-all <owner/repo>`);
  }
})().catch(e => { console.error('❌', e.message); process.exit(1); });
