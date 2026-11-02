#!/usr/bin/env python3
"""docs/md/ の Markdown(原本)から docs/html/ の閲覧用 HTML(ミラー)を生成する。

    python3 scripts/build_docs.py          # すべて生成し、原本のない古い HTML を消す
    python3 scripts/build_docs.py --check  # HTML が原本と一致するかだけを確かめる(不一致で終了コード 1)

- 標準ライブラリだけで動く。Markdown は docs/md/ で使う書き方だけに対応する
  (書き方は docs/md/index.md の「書き方」を参照)。
- 見た目は document-design-system の field-notes テーマ。テーマと CSS は
  scripts/docs-shell.html に書き出してあり、プラグインがなくても再生成できる。
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MD_DIR = ROOT / "docs" / "md"
HTML_DIR = ROOT / "docs" / "html"
SHELL = ROOT / "scripts" / "docs-shell.html"

FONTS = (
    '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
    "family=Source+Sans+3:wght@400;500;600;700&family=Source+Code+Pro:wght@400;500;600"
    '&family=Noto+Sans+JP:wght@400;500;700&display=swap">'
)

# テーマの欧文フォントは和文の字形を持たないため、和文フォントを後ろに足す
JA_FONT_CSS = """/* ---- 和文フォント(scripts/build_docs.py が追加) ---- */
:root {
  --display: 'Source Sans 3', 'Noto Sans JP', 'Hiragino Sans', 'Yu Gothic UI', Meiryo, system-ui, sans-serif;
  --sans: 'Source Sans 3', 'Noto Sans JP', 'Hiragino Sans', 'Yu Gothic UI', Meiryo, system-ui, sans-serif;
  --mono: 'Source Code Pro', ui-monospace, 'Noto Sans JP', monospace;
}
/* 和文の改行: 小書きの仮名や句読点で行を始めない。見出しは文節の切れ目で折り返す */
body { line-break: strict; }
h1, h2, h3, .lede { word-break: auto-phrase; }
/* 長いファイルパスなどの行内コードで、狭い画面のページが横にはみ出さないようにする */
:not(pre) > code { overflow-wrap: anywhere; }
"""

# type → (既定の pattern, 見出し上のラベル)
TYPES = {
    "requirements": ("contract", "要件定義"),
    "architecture": ("system", "アーキテクチャ"),
    "adr": ("record", "技術判断の記録"),
    "how-to": ("procedure", "手順書"),
    "explanation": ("learning", "解説"),
    "service-docs": ("suite", "ドキュメント一覧"),
}

# 目次を付ける pattern(document-patterns.md で toc が許されているもの)
TOC_PATTERNS = {"contract", "learning", "suite"}

ALERT_RE = re.compile(r"^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$")
LIST_RE = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")
STEP_RE = re.compile(r"^\d+\.\s+(.*)$")
FENCE_RE = re.compile(r"^```(\S*)\s*$")


def alert_class(kind: str, pattern: str) -> str:
    """GitHub のアラート記法を、pattern で許されたモジュールの class に対応させる"""
    if kind == "IMPORTANT":
        return "decision-statement" if pattern == "record" else "keypoint"
    if kind == "NOTE":
        return "evidence"
    if kind == "TIP":
        if pattern in ("procedure", "learning"):
            return "outcome"
        if pattern in ("incident", "system", "suite"):
            return "takeaway"
        return "keypoint"
    # WARNING / CAUTION
    return "danger" if pattern in ("procedure", "contract", "plan", "assurance") else "keypoint"


# ---------------------------------------------------------------- inline


def is_wide(ch: str) -> bool:
    return ord(ch) >= 0x2E80


def join_lines(lines: list[str]) -> str:
    """段落内の改行をつなぐ。和文どうしの間には空白を入れない"""
    text = ""
    for raw in lines:
        line = raw.strip()
        if not text:
            text = line
        elif line and (is_wide(text[-1]) or is_wide(line[0])):
            text += line
        elif line:
            text += " " + line
    return text


def rewrite_href(url: str, src: Path) -> str:
    """docs/md 内の .md へのリンクを、対応する .html へのリンクに書き換える"""
    if re.match(r"^[a-z]+:", url) or url.startswith("#"):
        return url
    path, _, anchor = url.partition("#")
    target = (src.parent / path).resolve()
    if path.endswith(".md") and MD_DIR in target.parents:
        path = path[:-3] + ".html"
    return path + (f"#{anchor}" if anchor else "")


def inline(text: str, src: Path) -> str:
    codes: list[str] = []

    def stash_code(m: re.Match) -> str:
        code = m.group(2)
        # CommonMark と同じく、両端の空白 1 つずつは外す(`` `x` `` のようにバッククォートを含めるため)
        if len(code) > 2 and code.startswith(" ") and code.endswith(" ") and code.strip():
            code = code[1:-1]
        codes.append(f"<code>{html.escape(code)}</code>")
        return f"\x00{len(codes) - 1}\x00"

    # 閉じるのは開きと同じ長さのバッククォートの並びだけ(長さの違う並びは中身として扱う)
    text = re.sub(r"(?<!`)(`+)(?!`)(.+?)(?<!`)\1(?!`)", stash_code, text)
    text = html.escape(text, quote=False)
    text = re.sub(
        r"\[([^\]]+)\]\(([^)\s]+)\)",
        lambda m: f'<a href="{html.escape(rewrite_href(m.group(2), src))}">{m.group(1)}</a>',
        text,
    )
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    return re.sub(r"\x00(\d+)\x00", lambda m: codes[int(m.group(1))], text)


def plain(text: str) -> str:
    """見出しの ID・目次用に、インライン記法を外した文字列"""
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    return text.replace("**", "").strip()


# ---------------------------------------------------------------- document


@dataclass
class Doc:
    src: Path
    meta: dict[str, str]
    body: list[str]
    pattern: str = ""
    label: str = ""
    ids: dict[str, int] = field(default_factory=dict)
    table_count: int = 0

    @property
    def rel(self) -> Path:
        return self.src.relative_to(MD_DIR)

    def make_id(self, text: str) -> str:
        base = re.sub(r"[\s　]+", "-", plain(text))
        base = re.sub(r"[\"'<>&?#%()（）「」『』:：、。,.]", "", base) or "section"
        n = self.ids.get(base, 0)
        self.ids[base] = n + 1
        return base if n == 0 else f"{base}-{n + 1}"


def parse_front_matter(src: Path) -> Doc:
    lines = src.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        sys.exit(f"{src.relative_to(ROOT)}: 先頭に front matter(--- で囲む)がありません")
    end = lines.index("---", 1)
    meta = {}
    for line in lines[1:end]:
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    for key in ("title", "type", "status", "updated", "summary"):
        if not meta.get(key):
            sys.exit(f"{src.relative_to(ROOT)}: front matter に {key} がありません")
    doc = Doc(src=src, meta=meta, body=lines[end + 1 :])
    default_pattern, default_label = TYPES.get(meta["type"], ("", ""))
    doc.pattern = meta.get("pattern") or default_pattern
    doc.label = meta.get("label") or default_label
    if not doc.pattern or not doc.label:
        sys.exit(f"{src.relative_to(ROOT)}: type が {meta['type']} のときは pattern と label を書いてください")
    return doc


def render_list(lines: list[str], i: int, doc: Doc) -> tuple[str, int]:
    """入れ子(インデント)に対応した箇条書き。lines[i] は箇条書きの行"""
    first = LIST_RE.match(lines[i])
    indent = len(first.group(1))
    tag = "ol" if first.group(2)[0].isdigit() else "ul"
    items: list[list[str]] = []
    while i < len(lines):
        line = lines[i]
        m = LIST_RE.match(line)
        if m and len(m.group(1)) == indent:
            items.append([m.group(3)])
            i += 1
        elif m and len(m.group(1)) > indent and items:
            sub, i = render_list(lines, i, doc)
            items[-1].append("\x01" + sub)
        elif line.strip() and not m and len(line) - len(line.lstrip()) > indent and items:
            items[-1].append(line.strip())  # 項目の続きの行
            i += 1
        else:
            break
    out = []
    for parts in items:
        text = [p for p in parts if not p.startswith("\x01")]
        subs = "".join(p[1:] for p in parts if p.startswith("\x01"))
        out.append(f"<li>{inline(join_lines(text), doc.src)}{subs}</li>")
    return f"<{tag}>{''.join(out)}</{tag}>", i


def split_row(line: str) -> list[str]:
    cells = re.split(r"(?<!\\)\|", line.strip().strip("|"))
    return [c.strip().replace("\\|", "|") for c in cells]


def render_blocks(lines: list[str], doc: Doc, top: bool = False) -> str:
    """ブロック要素を HTML にする。top=True のときだけ見出しと節を扱う"""
    out: list[str] = []
    i = 0
    in_section = False
    in_steps = False
    section_no = 0

    def close_steps() -> None:
        nonlocal in_steps
        if in_steps:
            out.append("</li></ol>")
            in_steps = False

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped or (stripped.startswith("<!--") and stripped.endswith("-->")):
            i += 1
            continue

        fence = FENCE_RE.match(stripped)
        if fence:
            lang = fence.group(1)
            j = i + 1
            while j < len(lines) and lines[j].strip() != "```":
                j += 1
            code = html.escape("\n".join(lines[i + 1 : j]), quote=False)
            label = f'<span class="lang">{html.escape(lang)}</span>' if lang else ""
            out.append(f"<pre>{label}{code}</pre>")
            i = j + 1
            continue

        heading = re.match(r"^(#{2,4})\s+(.*)$", stripped)
        if heading and top:
            level = len(heading.group(1))
            text = heading.group(2)
            if level == 2:
                close_steps()
                if in_section:
                    out.append("</section>")
                section_no += 1
                hid = doc.make_id(text)
                out.append(
                    f'<section class="section" aria-labelledby="{hid}">'
                    f'<div class="section-head"><span class="section-num">{section_no:02d}</span>'
                    f'<h2 id="{hid}">{inline(text, doc.src)}</h2></div>'
                )
                in_section = True
            else:
                step = STEP_RE.match(text) if doc.pattern == "procedure" and level == 3 else None
                if step:
                    # procedure の「### 1. 〜」は番号付きの手順(procedure-steps)にまとめる
                    out.append("</li><li>" if in_steps else '<ol class="procedure-steps"><li>')
                    in_steps = True
                    text = step.group(1)
                else:
                    close_steps()
                hid = doc.make_id(text)
                out.append(f'<h{level} id="{hid}">{inline(text, doc.src)}</h{level}>')
            i += 1
            continue

        if stripped.startswith(">"):
            quote = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quote.append(re.sub(r"^\s*> ?", "", lines[i]))
                i += 1
            alert = ALERT_RE.match(quote[0].strip()) if quote else None
            if alert:
                cls = alert_class(alert.group(1), doc.pattern)
                out.append(f'<div class="{cls}">{render_blocks(quote[1:], doc)}</div>')
            else:
                out.append(f"<blockquote>{render_blocks(quote, doc)}</blockquote>")
            continue

        if LIST_RE.match(line):
            rendered, i = render_list(lines, i, doc)
            out.append(rendered)
            continue

        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\|?\s*:?-{3,}", lines[i + 1].strip()):
            header = split_row(stripped)
            rows = []
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            caption = ""
            if i < len(lines) and lines[i].strip().startswith(": "):
                doc.table_count += 1
                caption = f"<caption>表 {doc.table_count}　{inline(lines[i].strip()[2:], doc.src)}</caption>"
                i += 1
            head = "".join(f"<th>{inline(c, doc.src)}</th>" for c in header)
            body = "".join(
                "<tr>" + "".join(f"<td>{inline(c, doc.src)}</td>" for c in row) + "</tr>" for row in rows
            )
            out.append(
                f'<div class="table-scroll"><table>{caption}<thead><tr>{head}</tr></thead>'
                f"<tbody>{body}</tbody></table></div>"
            )
            continue

        if stripped == "---":
            out.append("<hr>")
            i += 1
            continue

        para = []
        while i < len(lines):
            s = lines[i].strip()
            if (
                not s
                or FENCE_RE.match(s)
                or s.startswith((">", "|", "#"))
                or LIST_RE.match(lines[i])
                or s == "---"
            ):
                break
            para.append(s)
            i += 1
        if para:
            out.append(f"<p>{inline(join_lines(para), doc.src)}</p>")
        else:
            # どの規則にも当たらない行(例: 見出しの外での「#」)は段落として扱う
            out.append(f"<p>{inline(stripped, doc.src)}</p>")
            i += 1

    close_steps()
    if in_section:
        out.append("</section>")
    return "\n".join(out)


def render_toc(doc: Doc) -> str:
    heads = [m.group(1) for line in doc.body if (m := re.match(r"^##\s+(.*)$", line.strip()))]
    if doc.pattern not in TOC_PATTERNS or len(heads) < 4:
        return ""
    probe = Doc(src=doc.src, meta=doc.meta, body=[])  # 本文と同じ規則で ID を作る
    items = "".join(f'<li><a href="#{probe.make_id(h)}">{html.escape(plain(h))}</a></li>' for h in heads)
    return f'<nav class="toc" aria-label="目次"><p class="eyebrow">目次</p><ol>{items}</ol></nav>'


def render_doc(doc: Doc, shell: str) -> str:
    meta = doc.meta
    depth = len(doc.rel.parts) - 1
    up = "../" * depth
    source = f"{up}../md/{doc.rel.as_posix()}"
    index_link = "" if doc.rel.as_posix() == "index.md" else f'<a href="{up}index.html">ドキュメント一覧</a> · '
    meta_items = [
        ("状態", html.escape(meta["status"])),
        ("更新日", html.escape(meta["updated"])),
    ]
    if meta.get("audience"):
        meta_items.append(("読み手", html.escape(meta["audience"])))
    meta_items.append(("原本", f'<a href="{source}"><code>docs/md/{doc.rel.as_posix()}</code></a>'))
    meta_html = "".join(f"<span><strong>{k}:</strong> {v}</span>" for k, v in meta_items)

    body = render_blocks(doc.body, doc, top=True)
    article = (
        f"<!-- このファイルは scripts/build_docs.py が docs/md/{doc.rel.as_posix()} から生成しています。"
        f"直接編集せず、原本を直してから再生成してください。 -->\n"
        '<article class="doc">\n'
        '<header class="doc-hero">'
        f'<p class="eyebrow">{index_link}{html.escape(doc.label)}</p>'
        f"<h1>{inline(meta['title'], doc.src)}</h1>"
        f'<p class="lede">{inline(meta["summary"], doc.src)}</p>'
        f'<div class="doc-meta" aria-label="文書の情報">{meta_html}</div>'
        "</header>\n"
        f"{render_toc(doc)}\n{body}\n</article>"
    )
    page = shell.replace("<!-- @@TITLE -->", html.escape(plain(meta["title"])))
    page = page.replace("<!-- @@FONTS -->", FONTS)
    page = page.replace("<!-- @@BODY -->", article)
    page = page.replace('<html lang="en"', '<html lang="ja"', 1)
    page = re.sub(r'data-pattern="[^"]*"', f'data-pattern="{doc.pattern}"', page, count=1)
    return page


def load_shell() -> str:
    shell = SHELL.read_text(encoding="utf-8")
    marker = "/* ---- inlined from core/print.css ---- */"
    if marker not in shell:
        sys.exit("scripts/docs-shell.html に print.css の目印がありません")
    # print.css は最後に置く決まりなので、その直前に和文フォントの指定を入れる
    return shell.replace(marker, JA_FONT_CSS + "\n" + marker, 1)


def check_index(docs: list[Doc]) -> list[str]:
    """index.md の一覧に載っていない文書を返す(一覧の更新漏れの検出)"""
    index = MD_DIR / "index.md"
    text = index.read_text(encoding="utf-8") if index.exists() else ""
    return [
        d.rel.as_posix()
        for d in docs
        if d.rel.as_posix() != "index.md" and f"({d.rel.as_posix()})" not in text
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="生成せず、HTML が最新かだけを確かめる")
    args = parser.parse_args()

    shell = load_shell()
    docs = [parse_front_matter(p) for p in sorted(MD_DIR.rglob("*.md"))]
    expected = {HTML_DIR / d.rel.with_suffix(".html"): render_doc(d, shell) for d in docs}
    stale = [p for p in HTML_DIR.rglob("*.html") if p not in expected]
    problems = []

    unlisted = check_index(docs)
    if unlisted:
        problems.append("docs/md/index.md の一覧に載っていない文書: " + ", ".join(unlisted))

    if args.check:
        for path, content in expected.items():
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                problems.append(f"{path.relative_to(ROOT)} が原本と一致しません")
        problems += [f"{p.relative_to(ROOT)} に対応する原本がありません" for p in stale]
    else:
        for path, content in expected.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        for p in stale:
            p.unlink()
        print(f"{len(expected)} 件を生成しました(削除 {len(stale)} 件)", file=sys.stderr)

    if problems:
        print("\n".join(problems), file=sys.stderr)
        if args.check:
            print("python3 scripts/build_docs.py で再生成してください", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
