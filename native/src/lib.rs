//! JNI accepts transient secrets unlocked by Android Keystore; no plaintext files.
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jstring},
};
use std::panic::{AssertUnwindSafe, catch_unwind};
use union_core::{Identity, client};
use zeroize::Zeroizing;

fn reply(env: &mut JNIEnv<'_>, result: Result<String, String>) -> jstring {
    match result.and_then(|value| env.new_string(value).map_err(|e| e.to_string())) {
        Ok(value) => value.into_raw(),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}
fn identity(env: &JNIEnv<'_>, secret: &JByteArray<'_>) -> Result<Identity, String> {
    let size = env.get_array_length(secret).map_err(|e| e.to_string())?;
    if !(1..=4096).contains(&size) {
        return Err("Invalid identity size".into());
    }
    let bytes = Zeroizing::new(env.convert_byte_array(secret).map_err(|e| e.to_string())?);
    Identity::import_secret(&bytes).map_err(|e| e.to_string())
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_console_nativecore_UnionNative_generateSecret(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let bytes = Zeroizing::new(
            Identity::generate()
                .export_secret()
                .map_err(|e| e.to_string())?,
        );
        env.byte_array_from_slice(&bytes)
            .map(|a| a.into_raw())
            .map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    match result {
        Ok(array) => array,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_console_nativecore_UnionNative_peerId(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        Ok(identity(&env, &secret)?.peer_id().to_string())
    }))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    reply(&mut env, result)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_jlucraft_console_nativecore_UnionNative_execute(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    secret: JByteArray<'_>,
    input: JString<'_>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let identity = identity(&env, &secret)?;
        if env
            .call_method(&input, "length", "()I", &[])
            .and_then(|v| v.i())
            .map_err(|e| e.to_string())?
            > 65536
        {
            return Err("Request too large".into());
        }
        let input: String = env.get_string(&input).map_err(|e| e.to_string())?.into();
        if input.len() > 65536 {
            return Err("Request too large".into());
        }
        let input = serde_json::from_str(&input).map_err(|e| e.to_string())?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|e| e.to_string())?;
        let response = runtime
            .block_on(client::execute(identity, input))
            .map_err(|e| e.to_string())?;
        serde_json::to_string(&response).map_err(|e| e.to_string())
    }))
    .unwrap_or_else(|_| Err("Native operation failed".into()));
    reply(&mut env, result)
}
