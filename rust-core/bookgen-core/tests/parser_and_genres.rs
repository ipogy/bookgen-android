use bookgen_core::{
    classify_embedding, classify_theme_keywords, detect_markup_format_for_text, get_genre,
    list_genres, parse_markup_blocks, sanitize_section_output, AdmonitionKind, BookGenError,
    DocumentBlock, MarkupFormat,
};

#[test]
fn sanitize_handles_bom_nul_crlf_tilde_and_malformed_fences() {
    let dirty = "\u{feff}```asciidoc\r\n== Chapter 1\0\r\n\r\n\r\n[source,python]\r\n----\r\nprint('hi')\r\n----\r\n```";
    let clean = sanitize_section_output(dirty.to_string(), MarkupFormat::AsciiDoc);
    assert_eq!(
        clean,
        "== Chapter 1\n\n[source,python]\n----\nprint('hi')\n----"
    );

    // Unclosed outer wrapper fence must NOT crash or eat content
    let unclosed = "```markdown\n## Incomplete Stream";
    let kept = sanitize_section_output(unclosed.to_string(), MarkupFormat::Markdown);
    assert_eq!(kept, "```markdown\n## Incomplete Stream");

    // Empty / whitespace returns empty
    assert!(sanitize_section_output("   \r\n\t ".to_string(), MarkupFormat::Markdown).is_empty());
}

#[test]
fn parser_handles_unclosed_blocks_tilde_fences_admonitions_quotes_and_cjk() {
    let md_weird = "\
~~~rust
let nested = \"```not-an-end```\";
~~~

###### Deep Heading

- 日本語リスト項目一
- English item two

1. Step 1
2. Step 2

> [!WARNING]
> Check edge cases carefully.

> Simple quote line
> Second quote line

---

```python
# Unclosed code fence at EOF due to truncated AI stream
def half_written():
";
    let blocks = parse_markup_blocks(md_weird.to_string(), MarkupFormat::Markdown);
    assert!(
        matches!(blocks.first(), Some(DocumentBlock::CodeBlock { language: Some(l), .. }) if l == "rust")
    );
    assert!(blocks
        .iter()
        .any(|b| matches!(b, DocumentBlock::Heading { level: 6, text } if text == "Deep Heading")));
    assert!(blocks
        .iter()
        .any(|b| matches!(b, DocumentBlock::BulletList { items } if items.len() == 2)));
    assert!(blocks
        .iter()
        .any(|b| matches!(b, DocumentBlock::NumberedList { items } if items.len() == 2)));
    assert!(blocks.iter().any(|b| matches!(
        b,
        DocumentBlock::Admonition {
            kind: AdmonitionKind::Warning,
            ..
        }
    )));
    assert!(blocks
        .iter()
        .any(|b| matches!(b, DocumentBlock::Quote { .. })));
    assert!(blocks.iter().any(|b| matches!(b, DocumentBlock::Divider)));
    assert!(
        matches!(blocks.last(), Some(DocumentBlock::CodeBlock { language: Some(l), code }) if l == "python" && code.contains("half_written"))
    );

    let adoc_full = "\
:toc:

[cols=\"2\",options=\"header\"]
|===
| 列A | 列B
| 値1 | 値2
|===

[quote, Ada Lovelace]
____
That brain of mine is something more than merely mortal.
____

[stem]
++++
\\int_0^1 x^2 dx = \\frac{1}{3}
++++

NOTE: Inline tip here.

''''
";
    let adoc_blocks = parse_markup_blocks(adoc_full.to_string(), MarkupFormat::AsciiDoc);
    assert!(adoc_blocks.iter().any(|b| matches!(b, DocumentBlock::Table { headers, rows } if headers.len() == 2 && rows.len() == 1)));
    assert!(adoc_blocks.iter().any(
        |b| matches!(b, DocumentBlock::Quote { attribution: Some(a), .. } if a == "Ada Lovelace")
    ));
    assert!(adoc_blocks.iter().any(
        |b| matches!(b, DocumentBlock::MathBlock { latex } if latex.contains("\\frac{1}{3}"))
    ));
    assert!(adoc_blocks.iter().any(|b| matches!(
        b,
        DocumentBlock::Admonition {
            kind: AdmonitionKind::Note,
            ..
        }
    )));
    assert_eq!(
        detect_markup_format_for_text(adoc_full.to_string()),
        MarkupFormat::AsciiDoc
    );
    assert_eq!(
        detect_markup_format_for_text(md_weird.to_string()),
        MarkupFormat::Markdown
    );
}

#[test]
fn genre_catalog_and_embedding_classifier_validate_all_inputs() {
    let all = list_genres().expect("list all genres");
    assert_eq!(all.len(), 12);
    for g in &all {
        let fetched = get_genre(g.key.clone()).expect("fetch by key");
        assert!(!fetched.name.is_empty());
        assert!(!fetched.editor_guide.is_empty());
        assert!(!fetched.element_label.is_empty());
        assert!(!fetched.author_style.is_empty());
    }

    assert!(matches!(
        get_genre("   ".to_string()),
        Err(BookGenError::InvalidInput { .. })
    ));
    assert!(matches!(
        get_genre("nonexistent_genre".to_string()),
        Err(BookGenError::NotFound { .. })
    ));

    // Wrong embedding length & non-finite floats must return InvalidInput
    assert!(matches!(
        classify_embedding(vec![0.1; 100]),
        Err(BookGenError::InvalidInput { .. })
    ));
    let mut nan_vec = vec![0.1; 3072];
    nan_vec[42] = f32::NAN;
    assert!(matches!(
        classify_embedding(nan_vec),
        Err(BookGenError::InvalidInput { .. })
    ));

    // Zero vector safely returns finite similarity 0.0
    let zero_matches = classify_embedding(vec![0.0; 3072]).expect("zero vector safe");
    assert_eq!(zero_matches.len(), 12);
    assert!(zero_matches
        .iter()
        .all(|m| (m.similarity - 0.0).abs() < 1e-12));

    // Offline keyword routing spot-checks
    assert_eq!(
        classify_theme_keywords("TOEIC英文法と翻訳トレーニング".to_string())
            .expect("language")
            .key,
        "language_study"
    );
    assert_eq!(
        classify_theme_keywords("SaaS経営戦略とファイナンス".to_string())
            .expect("business")
            .key,
        "business"
    );
}
