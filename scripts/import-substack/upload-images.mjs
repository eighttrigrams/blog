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

function main() {
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
  console.log("Done!");
}

main();
