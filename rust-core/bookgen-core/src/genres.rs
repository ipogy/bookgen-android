use std::sync::OnceLock;

use serde::Deserialize;

use crate::error::{guard_ffi, BookGenError};

const EMBEDDING_DIM: usize = 3072;
const CATALOG_JSON: &str = include_str!("../assets/genres_catalog.json");
const EMBEDDINGS_BIN: &[u8] = include_bytes!("../assets/genre_embeddings.bin");

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, uniffi::Record)]
pub struct GenreInfo {
    pub key: String,
    pub name: String,
    pub seed_text: String,
    pub editor_guide: String,
    pub element_label: String,
    pub author_style: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GenreMatch {
    pub genre: GenreInfo,
    pub similarity: f64,
}

struct LoadedCatalog {
    genres: Vec<GenreInfo>,
    vectors: Vec<Vec<f32>>,
}

static CATALOG_CELL: OnceLock<Result<LoadedCatalog, BookGenError>> = OnceLock::new();

fn catalog() -> Result<&'static LoadedCatalog, BookGenError> {
    CATALOG_CELL
        .get_or_init(load_catalog)
        .as_ref()
        .map_err(Clone::clone)
}

fn load_catalog() -> Result<LoadedCatalog, BookGenError> {
    let genres: Vec<GenreInfo> = serde_json::from_str(CATALOG_JSON)?;
    let expected_bytes = genres
        .len()
        .checked_mul(EMBEDDING_DIM)
        .and_then(|n| n.checked_mul(4))
        .ok_or_else(|| BookGenError::parse("genre embedding byte size overflow"))?;
    if EMBEDDINGS_BIN.len() != expected_bytes {
        return Err(BookGenError::parse(
            "bundled genre_embeddings.bin byte size mismatch",
        ));
    }

    let mut vectors = Vec::with_capacity(genres.len());
    let stride = EMBEDDING_DIM.saturating_mul(4);
    for genre_chunk in EMBEDDINGS_BIN.chunks_exact(stride) {
        let mut vec = Vec::with_capacity(EMBEDDING_DIM);
        for &raw_f32 in genre_chunk.as_chunks::<4>().0 {
            vec.push(f32::from_le_bytes(raw_f32));
        }
        vectors.push(vec);
    }

    Ok(LoadedCatalog { genres, vectors })
}

/// Lists all 12 built-in BookGen genres with prompts, styling, and editor instructions.
#[uniffi::export]
pub fn list_genres() -> Result<Vec<GenreInfo>, BookGenError> {
    guard_ffi("list_genres", || Ok(catalog()?.genres.clone()))
}

/// Retrieves metadata and author instructions for a single genre key.
#[uniffi::export]
pub fn get_genre(genre_key: String) -> Result<GenreInfo, BookGenError> {
    guard_ffi("get_genre", || {
        let key = genre_key.trim();
        if key.is_empty() {
            return Err(BookGenError::invalid_input("genre_key must not be blank"));
        }
        catalog()?
            .genres
            .iter()
            .find(|g| g.key == key)
            .cloned()
            .ok_or_else(|| BookGenError::not_found("unknown genre_key"))
    })
}

/// Ranks the 12 bundled genres by cosine similarity against a 3072-dim embedding vector.
#[uniffi::export]
pub fn classify_embedding(query_vector: Vec<f32>) -> Result<Vec<GenreMatch>, BookGenError> {
    guard_ffi("classify_embedding", || {
        if query_vector.len() != EMBEDDING_DIM {
            return Err(BookGenError::invalid_input(format!(
                "expected {EMBEDDING_DIM}-dim embedding, got {}",
                query_vector.len()
            )));
        }
        if query_vector.iter().any(|x| !x.is_finite()) {
            return Err(BookGenError::invalid_input(
                "query_vector contains non-finite values",
            ));
        }

        let cat = catalog()?;
        let mut matches = Vec::with_capacity(cat.genres.len());
        for (genre, basis) in cat.genres.iter().zip(cat.vectors.iter()) {
            let similarity = cosine_similarity(&query_vector, basis);
            matches.push(GenreMatch {
                genre: genre.clone(),
                similarity,
            });
        }
        matches.sort_by(|a, b| b.similarity.total_cmp(&a.similarity));
        Ok(matches)
    })
}

/// Fast offline keyword fallback classifier when an embedding API call is unavailable.
#[uniffi::export]
pub fn classify_theme_keywords(theme: String) -> Result<GenreInfo, BookGenError> {
    guard_ffi("classify_theme_keywords", || {
        let trimmed = theme.trim();
        if trimmed.is_empty() {
            return Err(BookGenError::invalid_input("theme must not be blank"));
        }

        let lower = trimmed.to_lowercase();
        let cat = catalog()?;
        let mut best_idx: usize = 0;
        let mut best_score: i32 = -1;

        for (idx, genre) in cat.genres.iter().enumerate() {
            let score = score_keywords(&lower, genre);
            if score > best_score {
                best_score = score;
                best_idx = idx;
            }
        }

        cat.genres
            .get(best_idx)
            .cloned()
            .ok_or_else(|| BookGenError::parse("genre catalog empty"))
    })
}

fn cosine_similarity(a: &[f32], b: &[f32]) -> f64 {
    let mut dot = 0.0_f64;
    let mut norm_a = 0.0_f64;
    let mut norm_b = 0.0_f64;

    for (va, vb) in a.iter().zip(b.iter()) {
        let fa = f64::from(*va);
        let fb = f64::from(*vb);
        dot += fa * fb;
        norm_a += fa * fa;
        norm_b += fb * fb;
    }

    let denom = norm_a.sqrt() * norm_b.sqrt();
    if denom <= 0.0 || !denom.is_finite() {
        0.0
    } else {
        (dot / denom).clamp(-1.0, 1.0)
    }
}

fn score_keywords(theme_lower: &str, genre: &GenreInfo) -> i32 {
    let keywords: &[&str] = match genre.key.as_str() {
        "tech" => &[
            "rust",
            "kotlin",
            "android",
            "ai",
            "llm",
            "api",
            "sql",
            "linux",
            "cloud",
            "docker",
            "プログラミング",
            "開発",
            "アーキテクチャ",
            "インフラ",
            "データベース",
            "システム",
            "ソフトウェア",
            "設計",
        ],
        "business" => &[
            "marketing",
            "finance",
            "saas",
            "startup",
            "strategy",
            "ビジネス",
            "経営",
            "戦略",
            "起業",
            "マーケティング",
            "会計",
            "財務",
            "組織",
            "営業",
        ],
        "culture_history" => &[
            "history",
            "culture",
            "philosophy",
            "歴史",
            "文化",
            "思想",
            "哲学",
            "日本史",
            "世界史",
            "民俗",
            "社会",
        ],
        "academic_science" => &[
            "physics", "math", "biology", "quantum", "science", "量子", "物理", "数学", "生物",
            "化学", "研究", "科学", "宇宙",
        ],
        "language_study" => &[
            "english",
            "grammar",
            "toeic",
            "japanese",
            "英語",
            "語学",
            "英文法",
            "翻訳",
            "発音",
            "単語",
            "会話",
        ],
        "exam_prep" => &[
            "exam",
            "certification",
            "資格",
            "試験",
            "検定",
            "合格",
            "過去問",
            "簿記",
            "宅建",
            "対策",
            "問題集",
        ],
        "travel_geography" => &[
            "travel",
            "guide",
            "city",
            "旅行",
            "旅",
            "紀行",
            "街歩き",
            "地理",
            "観光",
            "鉄道",
        ],
        "lifestyle_food" => &[
            "coffee",
            "cooking",
            "recipe",
            "wine",
            "料理",
            "レシピ",
            "コーヒー",
            "食",
            "暮らし",
            "園芸",
            "インテリア",
            "趣味",
        ],
        "health_fitness" => &[
            "health",
            "fitness",
            "sleep",
            "training",
            "健康",
            "睡眠",
            "筋トレ",
            "栄養",
            "運動",
            "医学",
            "ヨガ",
            "食事",
        ],
        "fiction" => &[
            "novel",
            "story",
            "fantasy",
            "sci-fi",
            "小説",
            "物語",
            "ファンタジー",
            "ミステリー",
            "主人公",
            "シナリオ",
            "異世界",
        ],
        "self_help_career" => &[
            "career",
            "habit",
            "productivity",
            "mindset",
            "キャリア",
            "習慣",
            "思考法",
            "生産性",
            "自己啓発",
            "時間術",
            "仕事術",
        ],
        "subculture_entertainment" => &[
            "game",
            "anime",
            "manga",
            "cinema",
            "ゲーム",
            "アニメ",
            "漫画",
            "映画",
            "音楽",
            "サブカル",
            "vtuber",
        ],
        _ => &[],
    };

    let mut score = 0;
    for kw in keywords {
        if theme_lower.contains(kw) {
            score += 10;
        }
    }
    for seed_tok in genre
        .seed_text
        .split(|c: char| c.is_whitespace() || c == '・' || c == '、')
    {
        let tok = seed_tok.trim().to_lowercase();
        if tok.chars().count() >= 2 && theme_lower.contains(&tok) {
            score += 3;
        }
    }
    score
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_all_12_genres_and_self_similarity_ranks_first(
    ) -> Result<(), Box<dyn std::error::Error>> {
        let genres = list_genres()?;
        assert_eq!(genres.len(), 12);
        let tech = get_genre("tech".to_string())?;
        assert!(!tech.editor_guide.is_empty());
        assert!(!tech.author_style.is_empty());

        let cat = catalog()?;
        let exam_idx = cat
            .genres
            .iter()
            .position(|g| g.key == "exam_prep")
            .ok_or("missing exam_prep")?;
        let exam_vec = cat.vectors.get(exam_idx).ok_or("missing vec")?.clone();
        let ranked = classify_embedding(exam_vec)?;
        let top = ranked.first().ok_or("missing top")?;
        assert_eq!(top.genre.key, "exam_prep");
        assert!(top.similarity > 0.999);
        Ok(())
    }

    #[test]
    fn keyword_classifier_routes_domain_themes_correctly() -> Result<(), Box<dyn std::error::Error>>
    {
        let rust_hit = classify_theme_keywords("RustとKotlin UniFFIアプリ開発".to_string())?;
        assert_eq!(rust_hit.key, "tech");
        let novel_hit = classify_theme_keywords("異世界ファンタジー小説の主人公".to_string())?;
        assert_eq!(novel_hit.key, "fiction");
        Ok(())
    }
}
