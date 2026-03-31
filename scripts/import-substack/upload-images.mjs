import { execSync } from "child_process";
import { mkdirSync, existsSync, readFileSync } from "fs";
import { createHash } from "crypto";
import Database from "better-sqlite3";

const creds = Object.fromEntries(
  readFileSync(new URL(".ftp-credentials", import.meta.url), "utf8")
    .trim()
    .split("\n")
    .map((l) => l.split("=", 2))
);
const FTP_USER = creds.FTP_USER;
const FTP_PASS = creds.FTP_PASS;
const FTP_HOST = creds.FTP_HOST;

const DB_PATH = "/Users/daniel/Workspace/plurama.eighttrigrams/blog/data/blog.db";
const LOCAL_DIR = "/tmp/substack-images-download";

const db = new Database(DB_PATH);

function ftpUpload(localPath, remotePath) {
  execSync(
    `curl -s -u '${FTP_USER}:${FTP_PASS}' -T '${localPath}' 'ftp://${FTP_HOST}/${remotePath}'`,
    { timeout: 60000 }
  );
}

function ftpMkdir(dir) {
  try {
    execSync(
      `curl -s -u '${FTP_USER}:${FTP_PASS}' 'ftp://${FTP_HOST}/' -Q 'MKD ${dir}'`,
      { timeout: 15000 }
    );
  } catch (_) {}
}

function getExtFromUrl(url) {
  if (url.includes(".jpeg") || url.includes(".jpg")) return "jpg";
  if (url.includes(".png")) return "png";
  if (url.includes(".gif")) return "gif";
  if (url.includes(".webp")) return "webp";
  return "jpg";
}

async function main() {
  if (!existsSync(LOCAL_DIR)) mkdirSync(LOCAL_DIR, { recursive: true });

  ftpMkdir("blog-images");

  const articles = db
    .prepare("SELECT article_id, content FROM articles")
    .all();

  const replaceStmt = db.prepare(
    "UPDATE articles SET content = REPLACE(content, ?, ?) WHERE article_id = ?"
  );

  for (const article of articles) {
    const urls = [
      ...article.content.matchAll(
        /https:\/\/substackcdn\.com\/image\/fetch\/[^)]+/g
      ),
    ].map((m) => m[0]);

    if (urls.length === 0) continue;

    const articleDir = `${LOCAL_DIR}/${article.article_id}`;
    if (!existsSync(articleDir)) mkdirSync(articleDir, { recursive: true });

    ftpMkdir(`blog-images/${article.article_id}`);

    console.log(`\n${article.article_id}: ${urls.length} images`);

    const seen = new Set();
    for (let i = 0; i < urls.length; i++) {
      const url = urls[i];
      if (seen.has(url)) continue;
      seen.add(url);

      const ext = getExtFromUrl(url);
      const hash = createHash("md5").update(url).digest("hex").slice(0, 8);
      const filename = `${i + 1}-${hash}.${ext}`;
      const localPath = `${articleDir}/${filename}`;
      const relativePath = `blog-images/${article.article_id}/${filename}`;

      console.log(`  [${i + 1}] downloading...`);
      try {
        execSync(`curl -sL -o '${localPath}' '${url}'`, { timeout: 30000 });
      } catch (e) {
        console.error(`  [${i + 1}] download failed`);
        continue;
      }

      console.log(`  [${i + 1}] uploading -> ${relativePath}`);
      try {
        ftpUpload(localPath, relativePath);
      } catch (e) {
        console.error(`  [${i + 1}] upload failed`);
        continue;
      }

      replaceStmt.run(url, relativePath, article.article_id);
    }
  }

  const remaining = db
    .prepare(
      "SELECT COUNT(*) as c FROM articles WHERE content LIKE '%substackcdn.com%'"
    )
    .get();
  console.log(`\nRemaining Substack URLs: ${remaining.c} articles`);

  console.log("\n--- Fetching preview/cover images from Substack API ---");

  const slugToId = {};
  for (const a of articles) {
    const content = a.content;
    slugToId[a.article_id] = a.article_id;
  }

  const allApiArticles = [];
  for (let offset = 0; ; offset += 50) {
    const resp = await fetch(
      `https://eighttrigrams.substack.com/api/v1/archive?sort=new&limit=50&offset=${offset}`,
      { headers: { "User-Agent": "Mozilla/5.0" } }
    );
    const data = await resp.json();
    if (!Array.isArray(data) || data.length === 0) break;
    allApiArticles.push(...data);
  }

  const SLUG_TO_ID = {
    "clojure-tutorial": 1,
    "haskell-tutorial": 2,
    "can-we-grasp-truth-as-a-whole": 3,
    "categories-and-conflict": 4,
    "schisms-schisms-and-more-schisms": 5,
    "what-kind-of-thing-is-an-object": 6,
    "small-functions-and-the-amazing-extract": 7,
    "best-way-to-live": 8,
    "models-are-predictive-simulations": 9,
    "from-scientific-method-to-consensus": 10,
    "new-tool-in-the-box": 11,
    "here-to-stay": 12,
    "testing-functional-requirements": 13,
    "the-triangulation-method": 14,
    "taking-the-toys-away": 15,
    "sphere-whitepaper": 16,
    "hallucination-and-facticity": 17,
    "source-of-truth-in-swe": 18,
    "big-ball-of-mud-vs-your-coding-agent": 19,
    "mindless-drones": 20,
    "rhizome": 21,
    "category-theory-and-knowledge-databases": 22,
    "relations-all-the-way-down": 23,
    "personalist": 24,
    "the-spark-of-truth": 25,
    "the-purpose-of-a-system": 26,
    "timebased-publishing": 27,
    "running-in-circles": 28,
    "trusted-sources": 29,
  };

  const updatePreview = db.prepare(
    "UPDATE article_meta SET preview_image = ? WHERE article_id = ?"
  );

  for (const apiArticle of allApiArticles) {
    const articleId = SLUG_TO_ID[apiArticle.slug];
    if (!articleId || !apiArticle.cover_image) continue;

    const coverUrl = apiArticle.cover_image;
    const ext = getExtFromUrl(coverUrl);
    const hash = createHash("md5").update(coverUrl).digest("hex").slice(0, 8);
    const filename = `preview-${hash}.${ext}`;
    const localPath = `${LOCAL_DIR}/${articleId}/${filename}`;
    const remotePath = `blog-images/${articleId}/${filename}`;

    if (!existsSync(`${LOCAL_DIR}/${articleId}`))
      mkdirSync(`${LOCAL_DIR}/${articleId}`, { recursive: true });

    console.log(`  ${articleId} (${apiArticle.slug}): downloading cover...`);
    try {
      execSync(`curl -sL -o '${localPath}' '${coverUrl}'`, { timeout: 30000 });
      ftpMkdir(`blog-images/${articleId}`);
      ftpUpload(localPath, remotePath);
      updatePreview.run(remotePath, articleId);
      console.log(`    -> ${remotePath}`);
    } catch (e) {
      console.error(`    failed: ${e.message}`);
    }
  }

  console.log("\nDone!");
}

main();
