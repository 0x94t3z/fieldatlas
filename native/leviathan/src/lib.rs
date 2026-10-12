//! Read-only JNI boundary. No CLI, shell, network, writes or raw pointer handles.
use jni::{
    JNIEnv,
    objects::{JObject, JString},
    sys::{jint, jlong, jstring},
};
use leviathan::{
    card::CardOptions,
    query::{SearchRequest, Status, Store},
};
use std::{
    collections::HashMap,
    path::Path,
    sync::{
        Arc, Mutex, OnceLock,
        atomic::{AtomicI64, Ordering},
    },
};

struct Entry {
    store: Mutex<Store>,
    cancel: Box<dyn Fn() + Send + Sync>,
}
static STORES: OnceLock<Mutex<HashMap<i64, Arc<Entry>>>> = OnceLock::new();
static NEXT: AtomicI64 = AtomicI64::new(1);
fn stores() -> &'static Mutex<HashMap<i64, Arc<Entry>>> {
    STORES.get_or_init(Default::default)
}
fn entry(id: i64) -> Result<Arc<Entry>, String> {
    stores()
        .lock()
        .map_err(|_| "registry poisoned")?
        .get(&id)
        .cloned()
        .ok_or("closed search store".into())
}
fn open(path: &str) -> Result<i64, String> {
    let store = Store::open(Path::new(path), CardOptions::default()).map_err(|e| e.to_string())?;
    let interrupt = store.interrupt_handle();
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    if id <= 0 {
        return Err("search handle limit reached".into());
    }
    stores().lock().map_err(|_| "registry poisoned")?.insert(
        id,
        Arc::new(Entry {
            store: Mutex::new(store),
            cancel: Box::new(move || interrupt.interrupt()),
        }),
    );
    Ok(id)
}
fn search(id: i64, query: &str, limit: i32) -> Result<String, String> {
    if !(1..=50).contains(&limit) || query.trim().is_empty() || query.len() > 4096 {
        return Err("invalid search bounds".into());
    }
    let entry = entry(id)?;
    let store = entry.store.lock().map_err(|_| "search store poisoned")?;
    let result = store
        .search(&SearchRequest {
            query: query.into(),
            limit: limit as usize,
            fallback: false,
            ..Default::default()
        })
        .map_err(|e| e.to_string())?;
    if !matches!(result.status, Status::Ok) {
        return Err("search did not succeed".into());
    }
    // Expand IDs from the SAME immutable database. Snippets never become evidence.
    let mut passages = Vec::with_capacity(result.results.len());
    let mut bytes = 0;
    for card in result.results {
        let record = store
            .get(&card.id)
            .map_err(|e| e.to_string())?
            .ok_or("missing source record")?;
        if record.get("id").and_then(|v| v.as_str()) != Some(card.id.as_str()) {
            return Err("source identity mismatch".into());
        }
        bytes += record.to_string().len();
        if bytes > 4_194_304 {
            return Err("evidence exceeds search bound".into());
        }
        passages.push(record);
    }
    serde_json::to_string(&passages).map_err(|e| e.to_string())
}
fn close(id: i64) -> Result<(), String> {
    if let Some(entry) = stores()
        .lock()
        .map_err(|_| "registry poisoned")?
        .remove(&id)
    {
        (entry.cancel)(); // Arc keeps an in-flight read alive until it returns.
    }
    Ok(())
}
fn failure(env: &mut JNIEnv, message: String) {
    let _ = env.throw_new("java/lang/IllegalStateException", message);
}

// Every JNI entry contains Rust panics; none may unwind through the VM.
fn guarded<T>(operation: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(operation))
        .unwrap_or_else(|_| Err("native search failed".into()))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_xyz_fieldatlas_research_LeviathanNative_open(
    env: JNIEnv,
    _: JObject,
    path: JString,
) -> jlong {
    let mut env = env;
    let result = guarded(|| {
        let path: String = env.get_string(&path).map_err(|e| e.to_string())?.into();
        open(&path)
    });
    match result {
        Ok(id) => id,
        Err(e) => {
            failure(&mut env, e);
            0
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_xyz_fieldatlas_research_LeviathanNative_search(
    env: JNIEnv,
    _: JObject,
    id: jlong,
    query: JString,
    limit: jint,
) -> jstring {
    let mut env = env;
    let result = guarded(|| {
        let query: String = env.get_string(&query).map_err(|e| e.to_string())?.into();
        let answer = search(id, &query, limit)?;
        env.new_string(answer)
            .map(|v| v.into_raw())
            .map_err(|e| e.to_string())
    });
    match result {
        Ok(answer) => answer,
        Err(e) => {
            failure(&mut env, e);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_xyz_fieldatlas_research_LeviathanNative_interrupt(
    env: JNIEnv,
    _: JObject,
    id: jlong,
) {
    let mut env = env;
    if let Err(e) = guarded(|| {
        (entry(id)?.cancel)();
        Ok(())
    }) {
        failure(&mut env, e);
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_xyz_fieldatlas_research_LeviathanNative_close(
    env: JNIEnv,
    _: JObject,
    id: jlong,
) {
    let mut env = env;
    if let Err(e) = guarded(|| close(id)) {
        failure(&mut env, e);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn invalid_handles_and_bounds_fail_closed() {
        assert!(search(-1, "water", 5).is_err());
        assert!(search(-1, "water", 51).is_err());
        assert!(search(-1, "", 1).is_err());
        assert!(close(-1).is_ok());
        assert!(open("/nonexistent-fieldatlas-test/index.sqlite").is_err());
    }
    #[test]
    fn panic_does_not_cross_boundary() {
        assert!(guarded::<()>(|| panic!("test boundary")).is_err());
    }
    #[test]
    #[ignore = "requires FIELDATLAS_NATIVE_FIXTURE, a converted synthetic index"]
    fn converted_pack_search_and_close() {
        let path = std::env::var("FIELDATLAS_NATIVE_FIXTURE").expect("fixture path required");
        let id = open(&path).unwrap();
        let result = search(id, "Alto pump", 5).unwrap();
        assert!(result.contains("F-27"));
        assert!(result.contains("document_id"));
        (entry(id).unwrap().cancel)();
        assert!(!search(id, "Neris battery", 5).unwrap().is_empty());
        close(id).unwrap();
        assert!(search(id, "Alto pump", 5).is_err());
        close(id).unwrap();
    }
}
