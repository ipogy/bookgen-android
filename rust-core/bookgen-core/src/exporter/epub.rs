use std::fmt::Write as FmtWrite;
use std::io::{Seek, Write};
use std::sync::atomic::AtomicBool;

use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

use crate::error::BookGenError;
use crate::exporter::check_cancelled;
use crate::markup::{parse_markup_blocks, DocumentBlock};
use crate::models::{BookDetail, SectionStatus};

const BOOK_CSS: &str = r#"
body {
  font-family: serif;
  line-height: 1.7;
  margin: 1.5em;
  color: #1f1b16;
}
h1, h2, h3, h4 {
  font-family: sans-serif;
  line-height: 1.3;
  margin-top: 1.4em;
}
pre {
  background: #f4f1ea;
  border-left: 3px solid #6b5344;
  padding: 0.75em;
  overflow-x: auto;
  white-space: pre-wrap;
  font-family: monospace;
  font-size: 0.9em;
}
table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}
th, td {
  border: 1px solid #d4ccc0;
  padding: 0.45em 0.6em;
  text-align: left;
}
th {
  background: #ede6da;
}
aside.admonition {
  border-left: 4px solid #8a4b2a;
  background: #faf5ee;
  padding: 0.75em 1em;
  margin: 1em 0;
}
blockquote {
  border-left: 3px solid #b5a898;
  margin: 1em 0;
  padding-left: 1em;
  color: #4f463d;
}
"#;

pub(crate) fn write_epub_book<W: Write + Seek>(
    detail: &BookDetail,
    include_toc: bool,
    include_incomplete_notice: bool,
    cancel_flag: &AtomicBool,
    writer: W,
    mut on_fraction: impl FnMut(f64) -> Result<(), BookGenError>,
) -> Result<(), BookGenError> {
    let mut zip = ZipWriter::new(writer);
    let stored = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
    let deflated = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    // 1. OCF requires mimetype to be the very first, uncompressed entry.
    zip.start_file("mimetype", stored).map_err(map_zip_err)?;
    zip.write_all(b"application/epub+zip")?;

    // 2. container.xml
    zip.start_file("META-INF/container.xml", deflated)
        .map_err(map_zip_err)?;
    zip.write_all(
        br#"<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"#,
    )?;

    // 3. CSS
    zip.start_file("OEBPS/styles/book.css", deflated)
        .map_err(map_zip_err)?;
    zip.write_all(BOOK_CSS.as_bytes())?;

    // 4. Cover / Title page
    zip.start_file("OEBPS/cover.xhtml", deflated)
        .map_err(map_zip_err)?;
    let cover_xhtml = render_cover_xhtml(detail);
    zip.write_all(cover_xhtml.as_bytes())?;

    // 5. Chapters
    let total_chapters = detail.chapters.len().max(1);
    let mut manifest_items = String::new();
    let mut spine_items = String::new();
    let mut nav_links = String::new();

    let _ = writeln!(
        nav_links,
        "      <li><a href=\"cover.xhtml\">{}</a></li>",
        xml_escape(&detail.summary.title)
    );

    for (idx, ch) in detail.chapters.iter().enumerate() {
        check_cancelled(cancel_flag)?;
        let file_name = format!("chapter_{:03}.xhtml", idx.saturating_add(1));
        let item_id = format!("ch{:03}", idx.saturating_add(1));

        let xhtml = render_chapter_xhtml(ch, include_incomplete_notice, cancel_flag)?;
        zip.start_file(format!("OEBPS/{file_name}"), deflated)
            .map_err(map_zip_err)?;
        zip.write_all(xhtml.as_bytes())?;

        let _ = writeln!(
            manifest_items,
            "    <item id=\"{item_id}\" href=\"{file_name}\" media-type=\"application/xhtml+xml\"/>"
        );
        let _ = writeln!(spine_items, "    <itemref idref=\"{item_id}\"/>");
        let _ = writeln!(
            nav_links,
            "      <li><a href=\"{file_name}\">Chapter {}: {}</a></li>",
            ch.chapter.chapter_number,
            xml_escape(&ch.chapter.title)
        );

        on_fraction((idx.saturating_add(1) as f64) / (total_chapters as f64))?;
    }

    // 6. nav.xhtml
    check_cancelled(cancel_flag)?;
    zip.start_file("OEBPS/nav.xhtml", deflated)
        .map_err(map_zip_err)?;
    let nav_doc = render_nav_xhtml(&detail.summary.title, include_toc, &nav_links);
    zip.write_all(nav_doc.as_bytes())?;

    // 7. content.opf
    zip.start_file("OEBPS/content.opf", deflated)
        .map_err(map_zip_err)?;
    let opf_doc = render_content_opf(detail, include_toc, &manifest_items, &spine_items);
    zip.write_all(opf_doc.as_bytes())?;

    let mut inner = zip.finish().map_err(map_zip_err)?;
    inner.flush()?;
    Ok(())
}

fn render_cover_xhtml(detail: &BookDetail) -> String {
    let mut body = String::new();
    let _ = writeln!(body, "<h1>{}</h1>", xml_escape(&detail.summary.title));
    if !detail.summary.genre_name.is_empty() {
        let _ = writeln!(
            body,
            "<p><strong>Genre:</strong> {}</p>",
            xml_escape(&detail.summary.genre_name)
        );
    }
    if !detail.summary.target_audience.is_empty() {
        let _ = writeln!(
            body,
            "<p><strong>Target Audience:</strong> {}</p>",
            xml_escape(&detail.summary.target_audience)
        );
    }
    if !detail.summary.overview.trim().is_empty() {
        let _ = writeln!(
            body,
            "<p>{}</p>",
            xml_escape(detail.summary.overview.trim())
        );
    }
    wrap_xhtml(&detail.summary.title, &body)
}

fn render_chapter_xhtml(
    ch: &crate::models::ChapterWithSections,
    include_incomplete_notice: bool,
    cancel_flag: &AtomicBool,
) -> Result<String, BookGenError> {
    let mut body = String::new();
    let _ = writeln!(
        body,
        "<h1>Chapter {}: {}</h1>",
        ch.chapter.chapter_number,
        xml_escape(&ch.chapter.title)
    );
    if !ch.chapter.focus_area.trim().is_empty() {
        let _ = writeln!(
            body,
            "<p><em>{}</em></p>",
            xml_escape(ch.chapter.focus_area.trim())
        );
    }

    for sec in &ch.sections {
        check_cancelled(cancel_flag)?;
        let blocks = parse_markup_blocks(sec.content.clone(), sec.format);
        let has_heading = matches!(blocks.first(), Some(DocumentBlock::Heading { .. }));
        if !has_heading {
            let _ = writeln!(
                body,
                "<h2>{}.{} {}</h2>",
                ch.chapter.chapter_number,
                sec.section_number,
                xml_escape(&sec.title)
            );
        }

        if sec.status == SectionStatus::Completed && !blocks.is_empty() {
            for block in &blocks {
                append_block_xhtml(&mut body, block);
            }
        } else if include_incomplete_notice {
            let _ = writeln!(
                body,
                "<aside class=\"admonition\"><p><strong>NOTE:</strong> Section incomplete ({:?}).</p></aside>",
                sec.status
            );
        }
    }

    Ok(wrap_xhtml(&ch.chapter.title, &body))
}

fn append_block_xhtml(out: &mut String, block: &DocumentBlock) {
    match block {
        DocumentBlock::Heading { level, text } => {
            let tag_num = (*level).clamp(2, 6);
            let _ = writeln!(out, "<h{tag_num}>{}</h{tag_num}>", xml_escape(text));
        }
        DocumentBlock::Paragraph { text } => {
            let _ = writeln!(out, "<p>{}</p>", xml_escape(text));
        }
        DocumentBlock::CodeBlock { language, code } => {
            let cls = language
                .as_deref()
                .map(|l| format!(" class=\"language-{}\"", xml_escape(l)))
                .unwrap_or_default();
            let _ = writeln!(out, "<pre><code{cls}>{}</code></pre>", xml_escape(code));
        }
        DocumentBlock::Table { headers, rows } => {
            out.push_str("<table><thead><tr>");
            for h in headers {
                let _ = write!(out, "<th>{}</th>", xml_escape(h));
            }
            out.push_str("</tr></thead><tbody>");
            for row in rows {
                out.push_str("<tr>");
                for cell in row {
                    let _ = write!(out, "<td>{}</td>", xml_escape(cell));
                }
                out.push_str("</tr>");
            }
            out.push_str("</tbody></table>\n");
        }
        DocumentBlock::Admonition { kind, title, body } => {
            let heading = title
                .as_deref()
                .filter(|t| !t.trim().is_empty())
                .unwrap_or(kind.label());
            let _ = writeln!(
                out,
                "<aside class=\"admonition\"><p><strong>{}:</strong> {}</p></aside>",
                xml_escape(heading),
                xml_escape(body)
            );
        }
        DocumentBlock::MathBlock { latex } => {
            let _ = writeln!(out, "<pre class=\"math\">{}</pre>", xml_escape(latex));
        }
        DocumentBlock::BulletList { items } => {
            out.push_str("<ul>\n");
            for item in items {
                let _ = writeln!(out, "  <li>{}</li>", xml_escape(item));
            }
            out.push_str("</ul>\n");
        }
        DocumentBlock::NumberedList { items } => {
            out.push_str("<ol>\n");
            for item in items {
                let _ = writeln!(out, "  <li>{}</li>", xml_escape(item));
            }
            out.push_str("</ol>\n");
        }
        DocumentBlock::Quote { text, attribution } => {
            let _ = write!(out, "<blockquote><p>{}</p>", xml_escape(text));
            if let Some(attr) = attribution.as_deref().filter(|s| !s.trim().is_empty()) {
                let _ = write!(out, "<footer>— {}</footer>", xml_escape(attr));
            }
            out.push_str("</blockquote>\n");
        }
        DocumentBlock::Divider => {
            out.push_str("<hr/>\n");
        }
    }
}

fn wrap_xhtml(title: &str, body: &str) -> String {
    format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
         <!DOCTYPE html>\n\
         <html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ja\" xml:lang=\"ja\">\n\
         <head>\n\
           <meta charset=\"UTF-8\"/>\n\
           <title>{}</title>\n\
           <link rel=\"stylesheet\" type=\"text/css\" href=\"styles/book.css\"/>\n\
         </head>\n\
         <body>\n{}\n</body>\n</html>\n",
        xml_escape(title),
        body
    )
}

fn render_nav_xhtml(book_title: &str, include_toc: bool, nav_links: &str) -> String {
    let links = if include_toc { nav_links } else { "" };
    format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
         <!DOCTYPE html>\n\
         <html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ja\" xml:lang=\"ja\">\n\
         <head>\n\
           <meta charset=\"UTF-8\"/>\n\
           <title>{}</title>\n\
           <link rel=\"stylesheet\" type=\"text/css\" href=\"styles/book.css\"/>\n\
         </head>\n\
         <body>\n\
           <nav epub:type=\"toc\" id=\"toc\">\n\
             <h1>Table of Contents</h1>\n\
             <ol>\n{links}    </ol>\n\
           </nav>\n\
         </body>\n</html>\n",
        xml_escape(book_title)
    )
}

fn render_content_opf(
    detail: &BookDetail,
    include_toc: bool,
    manifest_items: &str,
    spine_items: &str,
) -> String {
    let modified = if detail.summary.updated_at.trim().is_empty() {
        "2026-01-01T00:00:00Z"
    } else {
        detail.summary.updated_at.trim()
    };
    let toc_spine = if include_toc {
        "    <itemref idref=\"nav\"/>\n"
    } else {
        ""
    };

    format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
         <package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" xml:lang=\"ja\">\n\
           <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n\
             <dc:identifier id=\"bookid\">urn:bookgen:{}</dc:identifier>\n\
             <dc:title>{}</dc:title>\n\
             <dc:language>ja</dc:language>\n\
             <dc:creator>Co-Author Studio (BookGen)</dc:creator>\n\
             <meta property=\"dcterms:modified\">{}</meta>\n\
           </metadata>\n\
           <manifest>\n\
             <item id=\"css\" href=\"styles/book.css\" media-type=\"text/css\"/>\n\
             <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n\
             <item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n\
         {manifest_items}  </manifest>\n\
           <spine>\n\
             <itemref idref=\"cover\"/>\n\
         {toc_spine}{spine_items}  </spine>\n\
         </package>\n",
        xml_escape(&detail.summary.id),
        xml_escape(&detail.summary.title),
        xml_escape(modified),
    )
}

fn xml_escape(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for ch in input.chars() {
        match ch {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&#39;"),
            '\0' => {}
            _ => out.push(ch),
        }
    }
    out
}

fn map_zip_err(err: zip::result::ZipError) -> BookGenError {
    BookGenError::Export {
        detail: format!("epub zip error: {err}"),
    }
}
