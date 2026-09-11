#!/usr/bin/env node
// 「心安」项目 → GitHub 推送 (适配已有授权仓库)
// 用法: node push_xinan.js <owner/repo> [目标子目录]
const fs = require('fs');
const path = require('path');

const TOKEN = fs.readFileSync('/storage/emulated/0/MT2/apks/gh_token.txt', 'utf8').trim();
const [,, repo, destDir = ''] = process.argv;
if (!repo) { console.error('用法: node push_xinan.js <owner/repo> [目标子目录]'); process.exit(1); }

const SRC = '/storage/emulated/0/MT2/心安项目';
const API = 'https://api.github.com';
const HEADERS = { 'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json', 'User-Agent': 'xinan-push' };

async function api(url, body, method = 'POST', retries = 5) {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetch(API + url, { method, headers: HEADERS, body: body ? JSON.stringify(body) : undefined });
      const j = await res.json().catch(() => ({}));
      if (res.ok) return j;
      if (res.status === 403 || res.status === 429) {
        const wait = 5000 * Math.pow(2, attempt) + Math.floor(Math.random() * 1000);
        console.log(`  限流, 等待 ${(wait/1000).toFixed(0)}s 重试...`);
        if (attempt < retries) { await new Promise(r => setTimeout(r, wait)); continue; }
      }
      throw new Error(`API ${res.status}: ${j.message || 'unknown'}`);
    } catch (e) {
      if (attempt < retries && /fetch failed|ECONNRESET|ETIMEDOUT|ECONNREFUSED/.test(e.message)) {
        const wait = 5000 * (attempt + 1);
        console.log(`  网络错误, ${(wait/1000).toFixed(0)}s 后重试 (${attempt+1}/${retries})...`);
        await new Promise(r => setTimeout(r, wait));
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
      if (['build', '.gradle', 'node_modules', '.git'].includes(entry.name)) continue;
      out.push(...collectFiles(full, prefix + entry.name + '/'));
    } else {
      const content = fs.readFileSync(full).toString('base64');
      out.push({ path: (prefix + entry.name).replace(/^\//, ''), content });
    }
  }
  return out;
}

(async () => {
  console.log(`📦 推送「心安」→ https://github.com/${repo}${destDir ? '/' + destDir : ''}`);
  console.log(`🔍 检查仓库 ${repo}...`);
  await api(`/repos/${repo}`, null, 'GET');

  const files = collectFiles(SRC, destDir ? destDir + '/' : '');
  console.log(`📄 共 ${files.length} 个文件`);

  console.log('⬆️ 上传内容...');
  const entries = [];
  const BATCH = 40;
  for (let i = 0; i < files.length; i += BATCH) {
    const batch = files.slice(i, i + BATCH);
    for (const f of batch) {
      const b = await api('/repos/' + repo + '/git/blobs', { content: f.content, encoding: 'base64' });
      entries.push({ path: f.path, mode: '100644', type: 'blob', sha: b.sha });
    }
    console.log(`  ${Math.min(i + BATCH, files.length)}/${files.length}...`);
  }

  console.log('🌳 创建 tree...');
  const branch = 'main';
  let baseTreeSha = null;
  try {
    const b = await api(`/repos/${repo}/branches/${branch}`, null, 'GET');
    baseTreeSha = b.commit.commit.tree.sha;
  } catch {}
  const tree = await api('/repos/' + repo + '/git/trees', { tree: entries, base_tree: baseTreeSha });

  console.log('📝 创建 commit...');
  let parentSha = null;
  try {
    const b = await api(`/repos/${repo}/branches/${branch}`, null, 'GET');
    parentSha = b.commit.sha;
  } catch {}
  const commit = await api('/repos/' + repo + '/git/commits', {
    message: `心安: 安卓焦虑情绪助手 (聊天+视频微表情分析) — ${new Date().toISOString().slice(0,10)}`,
    tree: tree.sha,
    parents: parentSha ? [parentSha] : [],
  });

  console.log('🚀 更新分支...');
  await api(`/repos/${repo}/git/refs/heads/${branch}`, { sha: commit.sha, force: true }, 'PATCH');

  console.log(`🎉 推送完成!`);
  console.log(`   https://github.com/${repo}/tree/${branch}/${destDir}`);
  console.log(`   commit: ${commit.sha.slice(0, 12)}`);
})().catch(e => { console.error('❌ 推送失败:', e.message); process.exit(1); });
