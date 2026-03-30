import * as cheerio from "cheerio";
import TurndownService from "turndown";

const BLOG_URL = "http://localhost:3028";
const SUBSTACK_BASE = "https://eighttrigrams.substack.com";

const ARTICLES = [
  { slug: "clojure-tutorial", pubDate: "2022-05-06T18:18:00.000Z" },
  { slug: "haskell-tutorial", pubDate: "2023-04-12T18:15:00.000Z" },
  { slug: "can-we-grasp-truth-as-a-whole", pubDate: "2023-06-11T22:55:00.000Z" },
  { slug: "categories-and-conflict", pubDate: "2023-07-15T19:02:00.000Z" },
  { slug: "schisms-schisms-and-more-schisms", pubDate: "2023-07-19T21:53:00.000Z" },
  { slug: "what-kind-of-thing-is-an-object", pubDate: "2023-07-30T19:42:00.000Z" },
  { slug: "small-functions-and-the-amazing-extract", pubDate: "2024-11-10T13:13:04.250Z" },
  { slug: "best-way-to-live", pubDate: "2025-05-25T18:34:53.241Z" },
  { slug: "models-are-predictive-simulations", pubDate: "2025-06-21T16:32:51.217Z" },
  { slug: "from-scientific-method-to-consensus", pubDate: "2025-06-28T11:23:51.131Z" },
  { slug: "new-tool-in-the-box", pubDate: "2025-08-03T11:15:58.158Z" },
  { slug: "here-to-stay", pubDate: "2025-08-11T22:26:54.486Z" },
  { slug: "testing-functional-requirements", pubDate: "2025-10-19T18:10:14.105Z" },
  { slug: "the-triangulation-method", pubDate: "2025-10-22T20:41:54.578Z" },
  { slug: "taking-the-toys-away", pubDate: "2025-11-22T21:38:07.621Z" },
  { slug: "sphere-whitepaper", pubDate: "2025-11-22T22:51:16.087Z" },
  { slug: "hallucination-and-facticity", pubDate: "2025-11-26T22:08:50.945Z" },
  { slug: "source-of-truth-in-swe", pubDate: "2025-12-06T15:09:32.066Z" },
  { slug: "big-ball-of-mud-vs-your-coding-agent", pubDate: "2025-12-07T13:36:39.312Z" },
  { slug: "mindless-drones", pubDate: "2025-12-18T15:53:41.330Z" },
  { slug: "rhizome", pubDate: "2025-12-25T20:11:24.852Z" },
  { slug: "category-theory-and-knowledge-databases", pubDate: "2025-12-25T23:22:07.914Z" },
  { slug: "relations-all-the-way-down", pubDate: "2025-12-26T23:46:46.231Z" },
  { slug: "personalist", pubDate: "2025-12-27T20:42:25.482Z" },
  { slug: "the-spark-of-truth", pubDate: "2026-01-12T22:11:02.330Z" },
  { slug: "the-purpose-of-a-system", pubDate: "2026-01-14T20:35:34.748Z" },
  { slug: "timebased-publishing", pubDate: "2026-01-15T18:19:25.232Z" },
  { slug: "running-in-circles", pubDate: "2026-02-18T00:16:00.521Z" },
  { slug: "trusted-sources", pubDate: "2026-02-18T23:00:42.470Z" },
];

function formatDate(dateStr) {
  const d = new Date(dateStr);
  const months = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
  ];
  const day = d.getUTCDate();
  const suffix =
    day === 1 || day === 21 || day === 31
      ? "st"
      : day === 2 || day === 22
        ? "nd"
        : day === 3 || day === 23
          ? "rd"
          : "th";
  return `${months[d.getUTCMonth()]} ${day}${suffix}, ${d.getUTCFullYear()}`;
}

function smartQuotes(text) {
  return text
    .replace(/"(\S)/g, "\u201c$1")
    .replace(/(\S)"/g, "$1\u201d")
    .replace(/"/g, "\u201d")
    .replace(/'(\S)/g, "\u2018$1")
    .replace(/(\S)'/g, "$1\u2019")
    .replace(/'/g, "\u2019");
}

function createTurndown() {
  const td = new TurndownService({
    headingStyle: "atx",
    bulletListMarker: "-",
    codeBlockStyle: "fenced",
  });

  td.addRule("footnoteAnchor", {
    filter: (node) =>
      node.tagName === "A" && node.classList.contains("footnote-anchor"),
    replacement: (_content, node) => {
      const num = node.textContent.trim();
      return `FOOTNOTE:fn${num}`;
    },
  });

  td.addRule("footnoteSection", {
    filter: (node) =>
      node.tagName === "DIV" && node.classList.contains("footnote"),
    replacement: () => "",
  });

  td.addRule("imageLink", {
    filter: (node) => {
      if (node.tagName !== "A") return false;
      const children = node.childNodes;
      return Array.from(children).some(
        (c) => c.tagName === "IMG" || c.querySelector?.("img")
      );
    },
    replacement: (_content, node) => {
      const img = node.querySelector("img");
      if (!img) return _content;
      const src = img.getAttribute("src") || "";
      const alt = img.getAttribute("alt") || "";
      return `![${alt}](${src})`;
    },
  });

  td.addRule("substackImage", {
    filter: (node) =>
      node.tagName === "IMG" && node.getAttribute("src")?.includes("substack"),
    replacement: (_content, node) => {
      const src = node.getAttribute("src") || "";
      const alt = node.getAttribute("alt") || "";
      return `![${alt}](${src})`;
    },
  });

  td.addRule("figcaption", {
    filter: "figcaption",
    replacement: (content) => `\n*${content.trim()}*\n`,
  });

  td.addRule("substackButton", {
    filter: (node) =>
      node.tagName === "A" &&
      (node.classList.contains("button") ||
        node.closest?.(".subscription-widget-wrap") != null ||
        node.closest?.(".subscribe-widget") != null),
    replacement: () => "",
  });

  td.addRule("subscriptionWidget", {
    filter: (node) => {
      const cls = node.className || "";
      return (
        cls.includes("subscription-widget") || cls.includes("subscribe-widget")
      );
    },
    replacement: () => "",
  });

  return td;
}

async function fetchArticle(slug) {
  const url = `${SUBSTACK_BASE}/p/${slug}`;
  console.log(`  Fetching ${url}...`);
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`Failed to fetch ${url}: ${resp.status}`);
  const html = await resp.text();
  const $ = cheerio.load(html);

  const title = smartQuotes($("h1.post-title, h1[class*='post-title']")
    .first()
    .text()
    .trim());
  const subtitle = smartQuotes($("h3.subtitle, h3[class*='subtitle']")
    .first()
    .text()
    .trim());

  const footnotes = [];
  $(".body.markup div.footnote").each((_i, el) => {
    const num = $(el).find("a.footnote-number").text().trim();
    const contentEl = $(el).find("div.footnote-content");

    contentEl.find("a").each((_j, a) => {
      const href = $(a).attr("href") || "";
      if (href.startsWith(SUBSTACK_BASE)) {
        $(a).attr("href", href);
      }
    });

    const td = createTurndown();
    const contentMd = td.turndown(contentEl.html() || "").trim();
    footnotes.push({ id: `fn${num}`, content: contentMd });
  });

  $(".body.markup div.footnote").remove();
  $(".body.markup .subscription-widget-wrap").remove();
  $(".body.markup .subscribe-widget").remove();

  const bodyHtml = $(".body.markup").html() || "";
  const td = createTurndown();
  let content = td.turndown(bodyHtml).trim();

  content = content.replace(/\n{3,}/g, "\n\n");

  return { title, subtitle, content, footnotes };
}

async function publishArticle(article, pubDateStr) {
  const dateFormatted = formatDate(pubDateStr);
  const originLine = `Originally published on Substack on ${dateFormatted}`;

  const fullContent = `*${originLine}*\n\n\n${article.content}`;
  const postContent = originLine;

  const footnotesField = article.footnotes
    .map((fn) => `FOOTNOTE:${fn.id} ${fn.content}`)
    .join("\n\n");

  const params = new URLSearchParams();
  params.set("title", article.title);
  params.set("subtitle", article.subtitle);
  params.set("content", fullContent);
  params.set("footnotes", footnotesField);
  params.set("addenda", "");
  params.set("post-content", postContent);
  params.set("publish", "on");

  console.log(`  Publishing "${article.title}"...`);

  const resp = await fetch(`${BLOG_URL}/articles`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params.toString(),
    redirect: "manual",
  });

  if (resp.status === 302) {
    const location = resp.headers.get("location");
    console.log(`  -> Created at ${location}`);
    return location;
  } else {
    const body = await resp.text();
    console.error(`  -> Failed (${resp.status}): ${body.substring(0, 200)}`);
    return null;
  }
}

async function main() {
  console.log("Substack -> Blog Import Pipeline");
  console.log("=================================\n");

  const dryRun = process.argv.includes("--dry-run");

  console.log(
    `Articles to import: ${ARTICLES.length} (oldest first)\n`
  );

  for (const entry of ARTICLES) {
    console.log(`\n--- ${entry.slug} (${formatDate(entry.pubDate)}) ---`);

    const article = await fetchArticle(entry.slug);
    console.log(`  Title: ${article.title}`);
    console.log(`  Subtitle: ${article.subtitle}`);
    console.log(`  Content length: ${article.content.length} chars`);
    console.log(`  Footnotes: ${article.footnotes.length}`);

    if (dryRun) {
      console.log("  [DRY RUN] Skipping publish");
      console.log(`  Preview (first 300 chars):\n${article.content.substring(0, 300)}\n`);
      if (article.footnotes.length > 0) {
        console.log("  Footnotes:");
        for (const fn of article.footnotes) {
          console.log(`    FOOTNOTE:${fn.id} ${fn.content.substring(0, 100)}`);
        }
      }
    } else {
      await publishArticle(article, entry.pubDate);
      await new Promise((r) => setTimeout(r, 500));
    }
  }

  console.log("\n=================================");
  console.log("Import complete!");
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
